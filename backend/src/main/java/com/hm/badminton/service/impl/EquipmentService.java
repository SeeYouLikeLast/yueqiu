package com.hm.badminton.service.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.Product;
import com.hm.badminton.entity.ProductCategory;
import com.hm.badminton.mapper.EquipmentMapper;
import com.hm.badminton.mapper.EquipmentOrderMapper;
import com.hm.badminton.service.IEquipmentService;
import com.hm.badminton.service.ISportCatalogService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    public EquipmentService(EquipmentMapper equipmentMapper,
                            ISportCatalogService sportCatalogService,
                            EquipmentOrderMapper equipmentOrderMapper) {
        this.equipmentMapper = equipmentMapper;
        this.sportCatalogService = sportCatalogService;
        this.equipmentOrderMapper = equipmentOrderMapper;
    }

    public List<ProductCategory> categories(String sportCode) {
        if (isAllSport(sportCode)) {
            return equipmentMapper.selectCategories(null);
        }
        String normalizedSport = sportCatalogService.require(sportCode).code();
        return equipmentMapper.selectCategories(normalizedSport);
    }

    public PageResult<Product> products(String sportCode, Long categoryId, String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).code();
        }
        String text = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Long total = equipmentMapper.countProducts(normalizedSport, categoryId, text);
        return new PageResult<>(equipmentMapper.selectProducts(normalizedSport, categoryId, text, safeSize, (safePage - 1) * safeSize),
                total == null ? 0 : total, safePage, safeSize);
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    public Product detail(Long id) {
        Product product = equipmentMapper.selectProduct(id);
        if (product == null) {
            throw new BusinessException(404, "装备不存在");
        }
        return product;
    }

    @Transactional
    public void addCart(Long userId, CartRequest request) {
        detail(request.productId());
        Integer count = equipmentMapper.countCart(userId, request.productId());
        if (count != null && count > 0) {
            equipmentMapper.increaseCart(userId, request.productId(), request.quantity());
        } else {
            equipmentMapper.insertCart(userId, request.productId(), request.quantity());
        }
    }

    public List<CartItem> cart(Long userId) {
        return equipmentMapper.selectCart(userId);
    }

    public void removeCart(Long userId, Long itemId) {
        equipmentMapper.deleteCart(userId, itemId);
    }

    @Transactional
    public Long createOrder(Long userId, CreateOrderRequest request) {
        List<OrderProduct> products = loadOrderProducts(userId, request.items());
        if (products.isEmpty()) {
            throw new BusinessException("购物车为空");
        }
        BigDecimal total = products.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        EquipmentMapper.InsertOrderRow row = new EquipmentMapper.InsertOrderRow();
        row.setUserId(userId);
        row.setTotalAmount(total);
        row.setAddress(request.address());
        equipmentMapper.insertOrder(row);
        long orderId = row.getId();
        for (OrderProduct item : products) {
            int updated = equipmentMapper.deductProductStock(item.productId(), item.quantity());
            if (updated == 0) {
                throw new BusinessException(item.name() + " 库存不足");
            }
            equipmentMapper.insertOrderItem(orderId, item.productId(), item.name(), item.coverUrl(), item.price(), item.quantity());
        }
        equipmentMapper.deleteCartByUser(userId);
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
                .map(order -> new OrderSummary(order.id(), order.userId(), order.totalAmount(), order.status(),
                        order.address(), order.createdAt(), equipmentOrderMapper.selectItems(order.id())))
                .toList();
    }

    private List<OrderProduct> loadOrderProducts(Long userId, List<OrderItemRequest> items) {
        if (items != null && !items.isEmpty()) {
            List<OrderProduct> result = new ArrayList<>();
            for (OrderItemRequest item : items) {
                Product product = detail(item.productId());
                result.add(new OrderProduct(product.id(), product.name(), product.coverUrl(), product.price(), item.quantity()));
            }
            return result;
        }
        return equipmentMapper.selectOrderProductsFromCart(userId).stream()
                .map(item -> new OrderProduct(item.productId(), item.name(), item.coverUrl(), item.price(), item.quantity()))
                .toList();
    }

    public record CartRequest(Long productId, @Min(1) Integer quantity) {
    }

    public record OrderItemRequest(Long productId, @Min(1) Integer quantity) {
    }

    public record CreateOrderRequest(List<OrderItemRequest> items, @NotBlank String address) {
    }

    public record ProductCreateRequest(
            Long categoryId,
            String sportCode,
            @NotBlank String name,
            @NotBlank String brand,
            String description,
            String coverUrl,
            BigDecimal price,
            Integer stock) {
    }

    private record OrderProduct(Long productId, String name, String coverUrl, BigDecimal price, Integer quantity) {
    }

    @Transactional
    public Long createProduct(ProductCreateRequest request) {
        EquipmentMapper.InsertProductRow row = new EquipmentMapper.InsertProductRow();
        row.setSportCode(sportCatalogService.require(request.sportCode()).code());
        row.setCategoryId(request.categoryId());
        row.setName(request.name());
        row.setBrand(request.brand());
        row.setDescription(request.description());
        row.setCoverUrl(request.coverUrl());
        row.setPrice(request.price() == null ? BigDecimal.ZERO : request.price());
        row.setStock(request.stock() == null ? 0 : request.stock());
        equipmentMapper.insertProduct(row);
        return row.getId();
    }
}

