package com.hm.badminton.service.social.impl;

import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.mapper.social.SocialMapper;
import com.hm.badminton.mapper.trade.VenueItemMapper;
import com.hm.badminton.service.social.IActivityMaintenanceService;
import com.hm.badminton.utils.CacheClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ActivityMaintenanceService implements IActivityMaintenanceService {

    private final SocialMapper socialMapper;
    private final VenueItemMapper venueItemMapper;
    private final CacheClient cacheClient;

    public ActivityMaintenanceService(SocialMapper socialMapper,
                                      VenueItemMapper venueItemMapper,
                                      CacheClient cacheClient) {
        this.socialMapper = socialMapper;
        this.venueItemMapper = venueItemMapper;
        this.cacheClient = cacheClient;
    }

    @Override
    @Transactional
    public ActivityRefreshResult refreshActivityTimes(LocalDateTime currentTime) {
        // 1. 每次启动及每日 00:05 都把 D0/D1 演示活动对齐到今天/明天；真实活动绝不改期。
        int refreshedDemoActivities = socialMapper.alignDemoActivitiesToRollingWindow(currentTime);
        // 2. 只有用户真实发起且已经过期的活动才转为“已结束”。
        int finishedUserActivities = socialMapper.finishExpiredUserActivities(currentTime);

        // 3. 场馆库存代表具体日期，不能简单忽略过期条件。把过期演示行接到每个商品
        //    当前最晚日期之后，可长期演示，同时不改动绑定真实 venue_id 的业务库存。
        int refreshedDemoInventories = refreshDemoInventories(currentTime.toLocalDate());
        return new ActivityRefreshResult(
                refreshedDemoActivities,
                finishedUserActivities,
                refreshedDemoInventories);
    }

    private int refreshDemoInventories(LocalDate currentDate) {
        // 演示单场初始库存过小会在少量测试下单后全部售罄；仅扩充排序绑定演示库存，
        // 已售和锁定数量保持不变，真实场馆库存完全不受影响。
        int refreshed = venueItemMapper.ensureDemoCourtCapacity();
        // 先把“明天起售”的整个演示窗口对齐到今天，保证用户查询今晚时有真实时段可选。
        refreshed += alignFutureDemoWindows(currentDate);
        List<VenueItemMapper.ExpiredDemoInventoryRow> expired =
                venueItemMapper.selectExpiredDemoInventories(currentDate);
        Map<Long, LocalDate> latestDateByProduct = new HashMap<>();
        for (VenueItemMapper.ExpiredDemoInventoryRow row : expired) {
            // 同一商品可能一次过期多行；游标确保新日期依次递增且不会互相重叠。
            LocalDate currentLatest = latestDateByProduct.getOrDefault(
                    row.getProductId(),
                    initialInventoryCursor(row.getMaxServiceDate(), currentDate));
            LocalDate nextDate = currentLatest.plusDays(1);
            int updated = venueItemMapper.rollDemoInventory(row.getId(), nextDate, currentDate);
            if (updated > 0) {
                latestDateByProduct.put(row.getProductId(), nextDate);
                cacheClient.delete(RedisConstants.VENUE_ITEM_DETAIL_KEY + row.getProductId());
                refreshed += updated;
            }
        }
        return refreshed;
    }

    private int alignFutureDemoWindows(LocalDate currentDate) {
        int refreshed = 0;
        for (VenueItemMapper.DemoInventoryWindowRow window : venueItemMapper.selectDemoInventoryWindows()) {
            if (window.getMinServiceDate() == null || !window.getMinServiceDate().isAfter(currentDate)) {
                continue;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(currentDate, window.getMinServiceDate());
            int updated = venueItemMapper.shiftDemoInventoryWindow(window.getProductId(), days);
            if (updated > 0) {
                cacheClient.delete(RedisConstants.VENUE_ITEM_DETAIL_KEY + window.getProductId());
                refreshed += updated;
            }
        }
        return refreshed;
    }

    private LocalDate initialInventoryCursor(LocalDate currentMax, LocalDate currentDate) {
        // 整个窗口都过期时，第一条从今天开始；仍有未来场次时，过期行接到最晚场次之后。
        return currentMax == null || currentMax.isBefore(currentDate)
                ? currentDate.minusDays(1)
                : currentMax;
    }
}
