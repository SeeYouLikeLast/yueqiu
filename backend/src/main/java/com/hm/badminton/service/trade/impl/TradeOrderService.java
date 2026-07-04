package com.hm.badminton.service.trade.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.dto.trade.EquipmentOrderCreateRequest;
import com.hm.badminton.dto.trade.EquipmentOrderItemRequest;
import com.hm.badminton.dto.trade.PaymentRequest;
import com.hm.badminton.dto.trade.VenueOrderCreateRequest;
import com.hm.badminton.entity.VenueCartItem;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.ITradeOrderService;
import com.hm.badminton.service.trade.IVenueItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TradeOrderService implements ITradeOrderService {

    private final IVenueItemService venueItemService;
    private final IEquipmentService equipmentService;

    public TradeOrderService(IVenueItemService venueItemService,
                             IEquipmentService equipmentService) {
        this.venueItemService = venueItemService;
        this.equipmentService = equipmentService;
    }

    // 创建订单和支付必须处于同一事务，避免出现已扣库存但未支付的半完成状态。
    @Override
    @Transactional
    public Map<String, Object> payDirect(Long userId, PaymentRequest request) {
        int tradeType = TradeType.require(request.getType());
        if (tradeType == TradeType.VENUE) {
            VenueOrder created = venueItemService.createOrder(userId,
                    new VenueOrderCreateRequest(request.getProductId(), request.getInventoryId()));
            VenueOrder paid = venueItemService.pay(userId, created.getId());
            return Map.of(
                    "orderId", paid.getId(),
                    "verifyCode", paid.getVerifyCode(),
                    "order", paid);
        }
        List<EquipmentOrderItemRequest> items = request.getProductId() == null
                ? null
                : List.of(new EquipmentOrderItemRequest(request.getProductId(), quantity(request.getQuantity())));
        Long orderId = equipmentService.createOrder(userId,
                new EquipmentOrderCreateRequest(items, request.getAddress()));
        equipmentService.pay(userId, orderId);
        return Map.of("orderId", orderId);
    }

    // 购物车结算会同时处理场所和装备，统一入口能减少前端重复请求。
    @Override
    @Transactional
    public Map<String, Object> payCart(Long userId, PaymentRequest request) {
        List<VenueCartItem> venueItems = venueItemService.cart(userId);
        List<Long> venueOrderIds = new ArrayList<>();
        for (VenueCartItem item : venueItems) {
            VenueOrder created = venueItemService.createOrder(userId,
                    new VenueOrderCreateRequest(item.getProductId(), item.getInventoryId()));
            VenueOrder paid = venueItemService.pay(userId, created.getId());
            venueOrderIds.add(paid.getId());
        }
        if (!venueItems.isEmpty()) {
            venueItemService.clearCart(userId);
        }

        Long equipmentOrderId = null;
        if (!equipmentService.cart(userId).isEmpty()) {
            equipmentOrderId = equipmentService.createOrder(userId,
                    new EquipmentOrderCreateRequest(null, request.getAddress()));
            equipmentService.pay(userId, equipmentOrderId);
        }

        if (venueOrderIds.isEmpty() && equipmentOrderId == null) {
            throw new BusinessException("购物车为空");
        }
        return Map.of(
                "venueOrderIds", venueOrderIds,
                "equipmentOrderId", equipmentOrderId == null ? "" : equipmentOrderId);
    }

    private int quantity(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }
}
