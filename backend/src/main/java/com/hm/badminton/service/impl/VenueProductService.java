package com.hm.badminton.service.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.entity.VenueProduct;
import com.hm.badminton.entity.VenueProductInventory;
import com.hm.badminton.mapper.VenueOrderMapper;
import com.hm.badminton.mapper.VenueProductMapper;
import com.hm.badminton.service.ISportCatalogService;
import com.hm.badminton.service.IVenueProductService;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VenueProductService implements IVenueProductService {

    private final VenueProductMapper venueProductMapper;
    private final ISportCatalogService sportCatalogService;
    private final VenueOrderMapper venueOrderMapper;

    public VenueProductService(VenueProductMapper venueProductMapper,
                               ISportCatalogService sportCatalogService,
                               VenueOrderMapper venueOrderMapper) {
        this.venueProductMapper = venueProductMapper;
        this.sportCatalogService = sportCatalogService;
        this.venueOrderMapper = venueOrderMapper;
    }

    public List<VenueProduct> products(String sportCode, String amapPlaceId, Long venueId, Integer placeRank, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 30);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).code();
        }
        String placeId = amapPlaceId == null || amapPlaceId.isBlank() ? null : amapPlaceId.trim();
        return venueProductMapper.selectProducts(normalizedSport, placeId, venueId, placeRank, null, null, safeLimit, null)
                .stream()
                .map(this::toProduct)
                .toList();
    }

    public PageResult<VenueProduct> saleProducts(String sportCode, String productType, String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 30);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).code();
        }
        String type = productType == null || productType.isBlank() || "ALL".equalsIgnoreCase(productType) ? null : productType.trim();
        String text = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Long total = venueProductMapper.countSaleProducts(normalizedSport, type, text);
        List<VenueProduct> records = venueProductMapper.selectProducts(normalizedSport, null, null, null, type, text, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(this::toProduct)
                .toList();
        return new PageResult<>(records, total == null ? 0 : total, safePage, safeSize);
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    public VenueProduct detail(Long productId) {
        VenueProduct product = toProduct(venueProductMapper.selectProduct(productId));
        if (product == null) {
            throw new BusinessException(404, "场所商品不存在");
        }
        return product;
    }

    public List<VenueProductInventory> inventories(Long productId, LocalDate date) {
        detail(productId);
        return venueProductMapper.selectInventories(productId, date);
    }

    @Transactional
    public VenueOrder createOrder(Long userId, OrderCreateRequest request) {
        VenueProductMapper.ProductSale sale = loadSale(request.productId(), request.inventoryId());
        if (sale.availableStock() <= 0) {
            throw new BusinessException("该时段库存已售罄");
        }
        int updated = venueProductMapper.deductInventory(request.productId(), request.inventoryId());
        if (updated == 0) {
            throw new BusinessException("该时段库存已售罄");
        }

        VenueProductMapper.InsertVenueOrderRow row = new VenueProductMapper.InsertVenueOrderRow();
        String verifyCode = verifyCode();
        row.setUserId(userId);
        row.setProductId(sale.productId());
        row.setInventoryId(sale.inventoryId());
        row.setVenueId(sale.venueId());
        row.setAmapPlaceId(sale.amapPlaceId());
        row.setVenueName(sale.venueName());
        row.setProductTitle(sale.productTitle());
        row.setProductType(sale.productType());
        row.setServiceDate(sale.serviceDate());
        row.setStartTime(sale.startTime());
        row.setEndTime(sale.endTime());
        row.setAmount(sale.price());
        row.setVerifyCode(verifyCode);
        venueProductMapper.insertVenueOrder(row);
        return order(userId, row.getId());
    }

    @Transactional
    public VenueOrder quickPay(Long userId, Long productId, VenueContextRequest request) {
        Long inventoryId = venueProductMapper.selectFirstAvailableInventoryId(productId);
        if (inventoryId == null) {
            throw new BusinessException("该项目暂无可售时段");
        }
        VenueOrder order = createOrder(userId, new OrderCreateRequest(productId, inventoryId));
        applyVenueContext(userId, order.id(), request);
        return pay(userId, order.id());
    }

    private void applyVenueContext(Long userId, Long orderId, VenueContextRequest request) {
        if (request == null || request.venueName() == null || request.venueName().isBlank()) {
            return;
        }
        venueProductMapper.updateOrderVenueContext(userId, orderId, request.venueId(), request.amapPlaceId(), request.venueName().trim());
    }

    @Transactional
    public VenueOrder pay(Long userId, Long orderId) {
        int updated = venueProductMapper.payVenueOrder(userId, orderId);
        if (updated == 0) {
            throw new BusinessException("订单不存在或无法支付");
        }
        return order(userId, orderId);
    }

    public VenueOrder order(Long userId, Long orderId) {
        VenueOrder order = venueOrderMapper.selectByIdAndUserId(orderId, userId, VenueOrderMapper.ORDER_COLUMNS);
        if (order == null) {
            throw new BusinessException(404, "场所订单不存在");
        }
        return order;
    }

    public List<VenueOrder> myOrders(Long userId) {
        return venueOrderMapper.selectByUserId(userId, VenueOrderMapper.ORDER_COLUMNS);
    }

    private VenueProductMapper.ProductSale loadSale(Long productId, Long inventoryId) {
        VenueProductMapper.ProductSale sale = venueProductMapper.selectSale(productId, inventoryId);
        if (sale == null) {
            throw new BusinessException(404, "可购买时段不存在");
        }
        return sale;
    }

    private VenueProduct toProduct(VenueProductMapper.VenueProductRow row) {
        if (row == null) {
            return null;
        }
        return new VenueProduct(
                row.id(),
                row.venueId(),
                row.amapPlaceId(),
                row.venueName(),
                row.placeRank(),
                row.sportCode(),
                row.productType(),
                productTypeName(row.productType()),
                row.title(),
                row.description(),
                row.coverUrl(),
                row.price(),
                row.originalPrice(),
                splitTags(row.tags()),
                row.useRule(),
                row.refundRule(),
                row.availableStock(),
                row.saleStartAt(),
                row.saleEndAt());
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

    public record OrderCreateRequest(@NotNull Long productId, @NotNull Long inventoryId) {
    }

    public record VenueContextRequest(Long venueId, String amapPlaceId, String venueName) {
    }

}

