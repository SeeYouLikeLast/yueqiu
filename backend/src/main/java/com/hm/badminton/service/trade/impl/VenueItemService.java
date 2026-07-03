package com.hm.badminton.service.trade.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.VenueCartItem;
import com.hm.badminton.entity.VenueInventory;
import com.hm.badminton.entity.VenueItem;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.mapper.trade.VenueItemMapper;
import com.hm.badminton.mapper.trade.VenueOrderMapper;
import com.hm.badminton.service.catalog.ISportCatalogService;
import com.hm.badminton.service.trade.IVenueItemService;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VenueItemService implements IVenueItemService {

    private final VenueItemMapper venueItemMapper;
    private final ISportCatalogService sportCatalogService;
    private final VenueOrderMapper venueOrderMapper;

    public VenueItemService(VenueItemMapper venueItemMapper,
                            ISportCatalogService sportCatalogService,
                            VenueOrderMapper venueOrderMapper) {
        this.venueItemMapper = venueItemMapper;
        this.sportCatalogService = sportCatalogService;
        this.venueOrderMapper = venueOrderMapper;
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
        return new PageResult<>(records, total == null ? 0 : total, safePage, safeSize);
    }

    @Override
    public VenueItem detail(Long productId) {
        VenueItem product = toItem(venueItemMapper.selectItem(productId));
        if (product == null) {
            throw new BusinessException(404, "场所商品不存在");
        }
        return product;
    }

    @Override
    public List<VenueInventory> inventories(Long productId, LocalDate date) {
        detail(productId);
        return venueItemMapper.selectInventories(productId, date);
    }

    @Override
    @Transactional
    public void addCart(Long userId, OrderCreateRequest request) {
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
    public void clearCart(Long userId) {
        venueItemMapper.deleteVenueCartByUser(userId);
    }

    @Override
    @Transactional
    public VenueOrder createOrder(Long userId, OrderCreateRequest request) {
        VenueItemMapper.VenueItemSale sale = loadSale(request.getProductId(), request.getInventoryId());
        if (sale.getAvailableStock() <= 0) {
            throw new BusinessException("该时段已售罄");
        }
        int updated = venueItemMapper.deductInventory(request.getProductId(), request.getInventoryId());
        if (updated == 0) {
            throw new BusinessException("该时段已售罄");
        }

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreateRequest {
        @NotNull
        private Long productId;
        @NotNull
        private Long inventoryId;
    }
}


