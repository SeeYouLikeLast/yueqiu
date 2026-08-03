package com.hm.badminton.service.social.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.social.ActivityRequest;
import com.hm.badminton.dto.social.VenueActivityBookingRequest;
import com.hm.badminton.dto.social.VenueActivityBookingResult;
import com.hm.badminton.dto.trade.VenueOrderCreateRequest;
import com.hm.badminton.entity.VenueInventory;
import com.hm.badminton.entity.VenueItem;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.service.social.ISocialService;
import com.hm.badminton.service.social.IVenueActivityBookingService;
import com.hm.badminton.service.trade.IVenueItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * “购买场馆时段并立即发起约球”的组合业务服务。
 *
 * <p>它本身不重复实现订单和活动规则，而是编排两个已有 Service。外层事务会传播到内部方法，
 * 保证库存扣减、订单支付、活动和发起人成员记录一起提交或一起回滚。</p>
 */
@Service
public class VenueActivityBookingService implements IVenueActivityBookingService {

    private final IVenueItemService venueItemService;
    private final ISocialService socialService;

    public VenueActivityBookingService(IVenueItemService venueItemService, ISocialService socialService) {
        this.venueItemService = venueItemService;
        this.socialService = socialService;
    }

    /**
     * 将“购买一个明确的场馆库存时段”与“在相同时段发起约球”活动放进同一个事务。
     *
     * 1. 从数据库读取商品和库存，绝不信任前端传来的金额、时间或运动类型；
     * 2. 先校验库存时段仍可售且尚未开始；
     * 3. 创建并支付场馆订单，内部通过条件扣库存避免超卖；
     * 4. 使用订单对应的日期、开始和结束时间创建活动；
     * 5. 任一步失败则回滚订单、库存和活动，避免只购买成功或只发起成功。
     */
    @Override
    @Transactional
    public VenueActivityBookingResult bookAndCreateActivity(Long userId, VenueActivityBookingRequest request) {
        VenueItem item = venueItemService.detail(request.getProductId());
        VenueInventory inventory = venueItemService.inventories(request.getProductId(), null).stream()
                .filter(candidate -> request.getInventoryId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "可购买时段不存在"));
        if (inventory.getAvailableStock() == null || inventory.getAvailableStock() <= 0) {
            throw new BusinessException("该时段已售罄");
        }

        LocalDateTime startTime = LocalDateTime.of(inventory.getServiceDate(), inventory.getStartTime());
        LocalDateTime endTime = LocalDateTime.of(inventory.getServiceDate(), inventory.getEndTime());
        if (!startTime.isAfter(LocalDateTime.now()) || !endTime.isAfter(startTime)) {
            throw new BusinessException("请选择尚未开始的有效场馆时段");
        }

        VenueOrder created = venueItemService.createOrder(userId,
                new VenueOrderCreateRequest(request.getProductId(), request.getInventoryId()));
        VenueOrder paid = venueItemService.pay(userId, created.getId());

        ActivityRequest activityRequest = new ActivityRequest();
        activityRequest.setSportCode(item.getSportCode());
        activityRequest.setVenueId(null);
        activityRequest.setPlaceSource(blankToDefault(request.getPlaceSource(), "amap"));
        activityRequest.setPlaceId(request.getPlaceId().trim());
        activityRequest.setVenueName(request.getVenueName().trim());
        activityRequest.setTitle(item.getTitle() + " · 约球");
        activityRequest.setCity(request.getCity().trim());
        activityRequest.setStartTime(startTime);
        activityRequest.setEndTime(endTime);
        activityRequest.setMaxPlayers(request.getMaxPlayers() == null ? 4 : request.getMaxPlayers());
        activityRequest.setLevelRequired(blankToDefault(request.getLevelRequired(), "不限"));
        activityRequest.setFeeType("场地已购");
        Long activityId = socialService.createActivity(userId, activityRequest);

        return new VenueActivityBookingResult(paid.getOrderNo(), paid.getVerifyCode(), activityId);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
