package com.hm.badminton.service.trade.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.dto.trade.EquipmentOrderCreateRequest;
import com.hm.badminton.dto.trade.EquipmentOrderItemRequest;
import com.hm.badminton.dto.trade.PaymentRequest;
import com.hm.badminton.dto.trade.PaymentResult;
import com.hm.badminton.dto.trade.OrderReference;
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

/**
 * 普通交易的统一编排服务。
 *
 * <p>前端只需区分 {@code type=1} 场馆商品和 {@code type=2} 装备商品。本类负责路由到具体
 * 服务，并用外层事务把“创建订单 + 模拟支付”组成一个原子业务。真正的库存 SQL 和订单明细
 * 分别由 {@link IVenueItemService}、{@link IEquipmentService} 完成。</p>
 */
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
    public PaymentResult payDirect(Long userId, PaymentRequest request) {
        // 1. 统一入口根据 type 分流，1=场所商品，2=装备商品。
        int tradeType = TradeType.require(request.getType());
        if (tradeType == TradeType.VENUE) {
            // 2. 场所购买需要指定库存时段：先扣库存生成订单，再立即支付。
            VenueOrder created = venueItemService.createOrder(userId,
                    new VenueOrderCreateRequest(request.getProductId(), request.getInventoryId()));
            VenueOrder paid = venueItemService.pay(userId, created.getId());
            return PaymentResult.direct(paid.getOrderNo(), paid.getVerifyCode());
        }
        List<EquipmentOrderItemRequest> items = request.getProductId() == null
                ? null
                : List.of(new EquipmentOrderItemRequest(request.getProductId(), quantity(request.getQuantity())));
        // 3. 装备购买可直接买单品，也可在 productId 为空时从购物车结算。
        OrderReference order = equipmentService.createOrder(userId,
                new EquipmentOrderCreateRequest(items, request.getAddress()));
        equipmentService.pay(userId, order.getId());
        return PaymentResult.direct(order.getOrderNo(), null);
    }

    // 购物车结算会同时处理场所和装备，统一入口能减少前端重复请求。
    @Override
    @Transactional
    public PaymentResult payCart(Long userId, PaymentRequest request) {
        // 1. 先处理场所购物车：每个场所商品都对应一个明确库存时段，因此逐个创建订单。
        List<VenueCartItem> venueItems = venueItemService.cart(userId);
        List<String> venueOrderNos = new ArrayList<>();
        for (VenueCartItem item : venueItems) {
            VenueOrder created = venueItemService.createOrder(userId,
                    new VenueOrderCreateRequest(item.getProductId(), item.getInventoryId()));
            VenueOrder paid = venueItemService.pay(userId, created.getId());
            venueOrderNos.add(paid.getOrderNo());
        }
        if (!venueItems.isEmpty()) {
            venueItemService.clearCart(userId);
        }

        // 2. 装备购物车合并成一个装备订单，便于地址、总价和物流统一处理。
        OrderReference equipmentOrder = null;
        if (!equipmentService.cart(userId).isEmpty()) {
            equipmentOrder = equipmentService.createOrder(userId,
                    new EquipmentOrderCreateRequest(null, request.getAddress()));
            equipmentService.pay(userId, equipmentOrder.getId());
        }

        if (venueOrderNos.isEmpty() && equipmentOrder == null) {
            throw new BusinessException("购物车为空");
        }
        // 3. 方法整体事务提交后，场所订单、装备订单、库存扣减和购物车清理同时生效。
        return PaymentResult.cart(
                venueOrderNos,
                equipmentOrder == null ? null : equipmentOrder.getOrderNo());
    }

    private int quantity(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }
}
