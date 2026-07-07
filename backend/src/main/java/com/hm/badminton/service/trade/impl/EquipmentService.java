package com.hm.badminton.service.trade.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.trade.EquipmentCartRequest;
import com.hm.badminton.dto.trade.EquipmentCreateRequest;
import com.hm.badminton.dto.trade.EquipmentOrderCreateRequest;
import com.hm.badminton.dto.trade.EquipmentOrderItemRequest;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.Equipment;
import com.hm.badminton.entity.EquipmentCategory;
import com.hm.badminton.mapper.trade.EquipmentMapper;
import com.hm.badminton.mapper.trade.EquipmentOrderMapper;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.catalog.ISportCatalogService;
import com.hm.badminton.utils.CacheClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class EquipmentService implements IEquipmentService {

    private final EquipmentMapper equipmentMapper;
    private final ISportCatalogService sportCatalogService;
    private final EquipmentOrderMapper equipmentOrderMapper;
    private final CacheClient cacheClient;

    public EquipmentService(EquipmentMapper equipmentMapper,
            ISportCatalogService sportCatalogService,
            EquipmentOrderMapper equipmentOrderMapper,
            CacheClient cacheClient) {
        this.equipmentMapper = equipmentMapper;
        this.sportCatalogService = sportCatalogService;
        this.equipmentOrderMapper = equipmentOrderMapper;
        this.cacheClient = cacheClient;
    }

    public List<EquipmentCategory> categories(String sportCode) {
        if (isAllSport(sportCode)) {
            return equipmentMapper.selectCategories(null);
        }
        String normalizedSport = sportCatalogService.require(sportCode).getCode();
        return equipmentMapper.selectCategories(normalizedSport);
    }

    public PageResult<Equipment> items(String sportCode, Long categoryId, String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).getCode();
        }
        String text = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Long total = equipmentMapper.countEquipments(normalizedSport, categoryId, text);
        return new PageResult<>(
                equipmentMapper.selectEquipments(normalizedSport, categoryId, text, safeSize,
                        (safePage - 1) * safeSize),
                total == null ? 0 : total, safePage, safeSize);
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    public Equipment detail(Long id) {
        return cacheClient.queryWithPassThrough(
                RedisConstants.EQUIPMENT_DETAIL_KEY + id,
                Equipment.class,
                () -> equipmentMapper.selectEquipment(id),
                RedisConstants.CACHE_DETAIL_TTL,
                RedisConstants.CACHE_DETAIL_JITTER_SECONDS,
                "装备不存在");
    }

    @Transactional
    public void addCart(Long userId, EquipmentCartRequest request) {
        detail(request.getProductId());
        Integer count = equipmentMapper.countCart(userId, request.getProductId());
        if (count != null && count > 0) {
            equipmentMapper.increaseCart(userId, request.getProductId(), request.getQuantity());
        } else {
            equipmentMapper.insertCart(userId, request.getProductId(), request.getQuantity());
        }
    }

    public List<CartItem> cart(Long userId) {
        return equipmentMapper.selectCart(userId);
    }

    public void removeCart(Long userId, Long itemId) {
        equipmentMapper.deleteCart(userId, itemId);
    }

    @Override
    public void updateCartQuantity(Long userId, Long itemId, Integer quantity) {
        int safeQuantity = quantity == null || quantity < 1 ? 1 : Math.min(quantity, 99);
        int updated = equipmentMapper.updateCartQuantity(userId, itemId, safeQuantity);
        if (updated == 0) {
            throw new BusinessException(404, "购物车商品不存在");
        }
    }

    @Override
    public void clearCart(Long userId) {
        equipmentMapper.deleteCartByUser(userId);
    }

    // 从 payDirect() 进来：共用外层事务
    // 从其他地方单独调用 createOrder()：自己有事务保护。
    @Transactional
    public Long createOrder(Long userId, EquipmentOrderCreateRequest request) {
        boolean fromCart = request.getItems() == null || request.getItems().isEmpty();
        List<OrderEquipment> equipment = loadOrderEquipments(userId, request.getItems());
        if (equipment.isEmpty()) {
            throw new BusinessException("购物车为空");
        }
        BigDecimal total = equipment.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        EquipmentMapper.InsertOrderRow row = new EquipmentMapper.InsertOrderRow();
        row.setUserId(userId);
        row.setTotalAmount(total);
        row.setAddress(request.getAddress());
        equipmentMapper.insertOrder(row);
        long orderId = row.getId();
        for (OrderEquipment item : equipment) {
            int updated = equipmentMapper.deductEquipmentStock(item.getProductId(), item.getQuantity());
            if (updated == 0) {
                throw new BusinessException(item.getName() + " 库存不足");
            }
            cacheClient.delete(RedisConstants.EQUIPMENT_DETAIL_KEY + item.getProductId());
            equipmentMapper.insertOrderItem(orderId, item.getProductId(), item.getName(), item.getCoverUrl(),
                    item.getPrice(), item.getQuantity());
        }
        if (fromCart) {
            equipmentMapper.deleteCartByUser(userId);
        }
        return orderId;
    }

    @Transactional
    public void pay(Long userId, Long orderId) {
        int updated = equipmentMapper.payOrder(userId, orderId);
        if (updated == 0) {
            throw new BusinessException("订单不存在或无法支付");
        }
    }

    public List<OrderSummary> orders(Long userId) {
        return equipmentOrderMapper.selectByUserId(userId).stream()
                .map(order -> new OrderSummary(order.getId(), order.getUserId(), order.getTotalAmount(),
                        order.getStatus(),
                        order.getAddress(), order.getCreatedAt(), equipmentOrderMapper.selectItems(order.getId())))
                .toList();
    }

    private List<OrderEquipment> loadOrderEquipments(Long userId, List<EquipmentOrderItemRequest> items) {
        if (items != null && !items.isEmpty()) {
            List<OrderEquipment> result = new ArrayList<>();
            for (EquipmentOrderItemRequest item : items) {
                Equipment product = detail(item.getProductId());
                result.add(new OrderEquipment(product.getId(), product.getName(), product.getCoverUrl(),
                        product.getPrice(), item.getQuantity()));
            }
            return result;
        }
        return equipmentMapper.selectOrderEquipmentsFromCart(userId).stream()
                .map(item -> new OrderEquipment(item.getProductId(), item.getName(), item.getCoverUrl(),
                        item.getPrice(), item.getQuantity()))
                .toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OrderEquipment {
        private Long productId;
        private String name;
        private String coverUrl;
        private BigDecimal price;
        private Integer quantity;
    }

    @Transactional
    public Long createEquipment(EquipmentCreateRequest request) {
        EquipmentMapper.InsertEquipmentRow row = new EquipmentMapper.InsertEquipmentRow();
        row.setSportCode(sportCatalogService.require(request.getSportCode()).getCode());
        row.setCategoryId(request.getCategoryId());
        row.setName(request.getName());
        row.setBrand(request.getBrand());
        row.setDescription(request.getDescription());
        row.setCoverUrl(request.getCoverUrl());
        row.setPrice(request.getPrice() == null ? BigDecimal.ZERO : request.getPrice());
        row.setStock(request.getStock() == null ? 0 : request.getStock());
        equipmentMapper.insertEquipment(row);
        return row.getId();
    }
}
