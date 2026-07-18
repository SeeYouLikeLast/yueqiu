package com.hm.badminton.service.trade.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.trade.VenueOrderCreateRequest;
import com.hm.badminton.entity.VenueCartItem;
import com.hm.badminton.entity.VenueInventory;
import com.hm.badminton.entity.VenueItem;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.mapper.trade.VenueItemMapper;
import com.hm.badminton.mapper.trade.VenueOrderMapper;
import com.hm.badminton.service.catalog.ISportCatalogService;
import com.hm.badminton.service.trade.IVenueItemService;
import com.hm.badminton.utils.CacheClient;
import com.hm.badminton.vo.AgentVenueProductVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 场馆商品业务。
 *
 * <p>需要特别区分：高德返回的 {@code place} 是真实场所，{@code venue} 是平台自行售卖的
 * 团购/时段商品。创建订单时必须绑定一条 {@link VenueInventory}，订单会保存场所、商品、
 * 日期和时段快照，避免场所搜索顺序变化后历史订单失真。</p>
 */
@Service
public class VenueItemService implements IVenueItemService {

    private final VenueItemMapper venueItemMapper;
    private final ISportCatalogService sportCatalogService;
    private final VenueOrderMapper venueOrderMapper;
    private final CacheClient cacheClient;

    public VenueItemService(VenueItemMapper venueItemMapper,
                            ISportCatalogService sportCatalogService,
                            VenueOrderMapper venueOrderMapper,
                            CacheClient cacheClient) {
        this.venueItemMapper = venueItemMapper;
        this.sportCatalogService = sportCatalogService;
        this.venueOrderMapper = venueOrderMapper;
        this.cacheClient = cacheClient;
    }

    @Override
    public List<VenueItem> items(String sportCode, String amapPlaceId, Long venueId, Integer placeRank, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 30);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).getCode();
        }
        String placeId = amapPlaceId == null || amapPlaceId.isBlank() ? null : amapPlaceId.trim();
        return venueItemMapper.selectItems(normalizedSport, placeId, venueId, placeRank, null, null, safeLimit, null)
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Override
    public PageResult<VenueItem> saleItems(String sportCode, String productType, String keyword, int page, int size) {
        // 1. 正常按运动、商品类型、关键词分页查询。
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 30);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).getCode();
        }
        String type = productType == null || productType.isBlank() || "ALL".equalsIgnoreCase(productType) ? null : productType.trim();
        String text = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Long total = venueItemMapper.countSaleItems(normalizedSport, type, text);
        List<VenueItem> records = venueItemMapper.selectItems(normalizedSport, null, null, null, type, text, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(this::toItem)
                .toList();
        long safeTotal = total == null ? 0 : total;
        // 2. 演示数据较少时，第一页自动补齐其它类型/运动商品，避免列表只有一两条。
        if (safePage == 1 && text == null && records.size() < safeSize) {
            records = fillSaleItemPage(records, normalizedSport, type, safeSize);
            safeTotal = Math.max(safeTotal, records.size());
        }
        return new PageResult<>(records, safeTotal, safePage, safeSize);
    }

    @Override
    public VenueItem detail(Long productId) {
        return cacheClient.queryWithPassThrough(
                RedisConstants.VENUE_ITEM_DETAIL_KEY + productId,
                VenueItem.class,
                () -> toItem(venueItemMapper.selectItem(productId)),
                RedisConstants.CACHE_DETAIL_TTL,
                RedisConstants.CACHE_DETAIL_JITTER_SECONDS,
                "场所商品不存在");
    }

    @Override
    public List<VenueInventory> inventories(Long productId, LocalDate date) {
        detail(productId);
        return venueItemMapper.selectInventories(productId, date);
    }

    @Override
    public List<AgentVenueProductVO> agentCandidates(String sportCode,
                                                     Integer placeRank,
                                                     LocalDate targetDate,
                                                     LocalTime startTime,
                                                     LocalTime endTime,
                                                     BigDecimal maxBudget,
                                                     int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        String normalizedSport = sportCatalogService.require(sportCode).getCode();
        if (targetDate != null && startTime != null) {
            List<AgentVenueProductVO> exact = venueItemMapper.selectExactAgentCandidates(
                    normalizedSport, placeRank, targetDate, startTime, endTime, maxBudget, safeLimit);
            if (!exact.isEmpty()) {
                exact.forEach(item -> item.setMatchType("EXACT"));
                return exact;
            }
            LocalTime windowEnd = fallbackWindowEnd(startTime, endTime);
            List<AgentVenueProductVO> fallback = venueItemMapper.selectOneHourAgentCandidates(
                    normalizedSport, placeRank, targetDate, startTime, windowEnd, maxBudget, safeLimit);
            fallback.forEach(item -> item.setMatchType("ONE_HOUR_FALLBACK"));
            return fallback;
        }
        List<AgentVenueProductVO> upcoming = venueItemMapper.selectUpcomingAgentCandidates(
                normalizedSport, placeRank, maxBudget, safeLimit);
        upcoming.forEach(item -> item.setMatchType("UPCOMING"));
        return upcoming;
    }

    private LocalTime fallbackWindowEnd(LocalTime startTime, LocalTime requestedEnd) {
        LocalTime preferred = requestedEnd != null && requestedEnd.isAfter(startTime)
                ? requestedEnd : startTime.plusHours(4);
        return preferred.isAfter(startTime) ? preferred : LocalTime.MAX;
    }

    @Override
    @Transactional
    public void addCart(Long userId, VenueOrderCreateRequest request) {
        loadSale(request.getProductId(), request.getInventoryId());
        Integer count = venueItemMapper.countVenueCart(userId, request.getProductId(), request.getInventoryId());
        if (count != null && count > 0) {
            venueItemMapper.touchVenueCart(userId, request.getProductId(), request.getInventoryId());
        } else {
            venueItemMapper.insertVenueCart(userId, request.getProductId(), request.getInventoryId());
        }
    }

    @Override
    public List<VenueCartItem> cart(Long userId) {
        return venueItemMapper.selectVenueCart(userId);
    }

    @Override
    public void removeCart(Long userId, Long itemId) {
        venueItemMapper.deleteVenueCart(userId, itemId);
    }

    @Override
    public void updateCartQuantity(Long userId, Long itemId, Integer quantity) {
        int safeQuantity = quantity == null || quantity < 1 ? 1 : Math.min(quantity, 99);
        int updated = venueItemMapper.updateVenueCartQuantity(userId, itemId, safeQuantity);
        if (updated == 0) {
            throw new BusinessException(404, "购物车商品不存在");
        }
    }

    @Override
    public void clearCart(Long userId) {
        venueItemMapper.deleteVenueCartByUser(userId);
    }

    @Override
    @Transactional
    public VenueOrder createOrder(Long userId, VenueOrderCreateRequest request) {
        // 1. 锁定一个“商品 + 库存时段”组合。场所订单必须绑定具体日期和时间。
        VenueItemMapper.VenueItemSale sale = loadSale(request.getProductId(), request.getInventoryId());
        if (sale.getAvailableStock() <= 0) {
            throw new BusinessException("该时段已售罄");
        }
        // 2. 条件扣库存，SQL 中要求 available_stock > 0，防止并发超卖。
        int updated = venueItemMapper.deductInventory(request.getProductId(), request.getInventoryId());
        if (updated == 0) {
            throw new BusinessException("该时段已售罄");
        }
        // 3. 库存变化后删除商品详情缓存，让下一次详情重新读取可售状态。
        cacheClient.delete(RedisConstants.VENUE_ITEM_DETAIL_KEY + request.getProductId());

        // 4. 保存订单快照，避免高德场所顺序或商品信息变化影响历史订单展示。
        VenueItemMapper.InsertVenueOrderRow row = new VenueItemMapper.InsertVenueOrderRow();
        row.setUserId(userId);
        row.setProductId(sale.getProductId());
        row.setInventoryId(sale.getInventoryId());
        row.setVenueId(sale.getVenueId());
        row.setAmapPlaceId(sale.getAmapPlaceId());
        row.setVenueName(sale.getVenueName());
        row.setProductTitle(sale.getProductTitle());
        row.setProductType(sale.getProductType());
        row.setServiceDate(sale.getServiceDate());
        row.setStartTime(sale.getStartTime());
        row.setEndTime(sale.getEndTime());
        row.setAmount(sale.getPrice());
        row.setVerifyCode(verifyCode());
        venueItemMapper.insertVenueOrder(row);
        return order(userId, row.getId());
    }

    @Override
    @Transactional
    public VenueOrder pay(Long userId, Long orderId) {
        int updated = venueItemMapper.payVenueOrder(userId, orderId);
        if (updated == 0) {
            throw new BusinessException("订单不存在或无法支付");
        }
        return order(userId, orderId);
    }

    @Override
    public VenueOrder order(Long userId, Long orderId) {
        VenueOrder order = venueOrderMapper.selectByIdAndUserId(orderId, userId, VenueOrderMapper.ORDER_COLUMNS);
        if (order == null) {
            throw new BusinessException(404, "场所订单不存在");
        }
        return order;
    }

    @Override
    public List<VenueOrder> myOrders(Long userId) {
        return venueOrderMapper.selectByUserId(userId, VenueOrderMapper.ORDER_COLUMNS);
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    private List<VenueItem> fillSaleItemPage(List<VenueItem> records,
                                             String sportCode,
                                             String productType,
                                             int size) {
        LinkedHashMap<Long, VenueItem> merged = new LinkedHashMap<>();
        appendSaleItems(merged, records);
        if (productType != null && merged.size() < size) {
            appendSaleItems(merged, venueItemMapper.selectItems(sportCode, null, null, null, null, null, size * 2, 0)
                    .stream()
                    .map(this::toItem)
                    .toList());
        }
        if (sportCode != null && merged.size() < size) {
            appendSaleItems(merged, venueItemMapper.selectItems(null, null, null, null, null, null, size * 2, 0)
                    .stream()
                    .map(this::toItem)
                    .toList());
        }
        return merged.values().stream().limit(size).toList();
    }

    private void appendSaleItems(LinkedHashMap<Long, VenueItem> merged, List<VenueItem> records) {
        for (VenueItem item : records) {
            merged.putIfAbsent(item.getId(), item);
        }
    }

    private VenueItemMapper.VenueItemSale loadSale(Long productId, Long inventoryId) {
        VenueItemMapper.VenueItemSale sale = venueItemMapper.selectSale(productId, inventoryId);
        if (sale == null) {
            throw new BusinessException(404, "可购买时段不存在");
        }
        return sale;
    }

    private VenueItem toItem(VenueItemMapper.VenueItemRow row) {
        if (row == null) {
            return null;
        }
        return new VenueItem(
                row.getId(),
                row.getVenueId(),
                row.getAmapPlaceId(),
                row.getVenueName(),
                row.getPlaceRank(),
                row.getSportCode(),
                row.getProductType(),
                productTypeName(row.getProductType()),
                row.getTitle(),
                row.getDescription(),
                row.getCoverUrl(),
                row.getPrice(),
                row.getOriginalPrice(),
                splitTags(row.getTags()),
                row.getUseRule(),
                row.getRefundRule(),
                row.getAvailableStock(),
                row.getSaleStartAt(),
                row.getSaleEndAt());
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private String productTypeName(String type) {
        return switch (type) {
            case "TIME_PACKAGE" -> "畅打套餐";
            case "COURT_SLOT" -> "单场时段";
            case "COACH_LESSON" -> "私教课";
            default -> type;
        };
    }

    private String verifyCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}


