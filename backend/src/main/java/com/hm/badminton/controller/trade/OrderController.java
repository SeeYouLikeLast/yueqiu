package com.hm.badminton.controller.trade;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.SeckillOrder;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.ISeckillService;
import com.hm.badminton.service.trade.IVenueItemService;
import com.hm.badminton.utils.UserContext;
import com.hm.badminton.vo.OrderCardVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 按 type 查询普通订单与秒杀订单的统一入口。 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IVenueItemService venueItemService;
    private final IEquipmentService equipmentService;
    private final ISeckillService seckillService;
    private final UserContext userContext;

    public OrderController(IVenueItemService venueItemService,
                           IEquipmentService equipmentService,
                           ISeckillService seckillService,
                           UserContext userContext) {
        this.venueItemService = venueItemService;
        this.equipmentService = equipmentService;
        this.seckillService = seckillService;
        this.userContext = userContext;
    }

    @GetMapping("/{type:[12]}")
    public ApiResponse<List<OrderCardVO>> orders(@PathVariable Integer type) {
        Long userId = userContext.requireUserId();
        int tradeType = TradeType.require(type);
        return ApiResponse.ok(tradeType == TradeType.VENUE
                ? venueOrders(userId)
                : equipmentOrders(userId));
    }

    private List<OrderCardVO> venueOrders(Long userId) {
        List<OrderCardVO> result = new ArrayList<>();
        result.addAll(venueItemService.myOrders(userId).stream()
                .map(this::fromVenueOrder)
                .toList());
        result.addAll(seckillService.myOrders(userId, TradeType.VENUE).stream()
                .map(order -> fromSeckillOrder(order, "秒杀场所"))
                .toList());
        result.sort(Comparator.comparing(OrderCardVO::getMeta, Comparator.nullsLast(String::compareTo)).reversed());
        return result;
    }

    private List<OrderCardVO> equipmentOrders(Long userId) {
        List<OrderCardVO> result = new ArrayList<>();
        result.addAll(equipmentService.orders(userId).stream()
                .map(this::fromEquipmentOrder)
                .toList());
        result.addAll(seckillService.myOrders(userId, TradeType.EQUIPMENT).stream()
                .map(order -> fromSeckillOrder(order, "秒杀装备"))
                .toList());
        result.sort(Comparator.comparing(OrderCardVO::getMeta, Comparator.nullsLast(String::compareTo)).reversed());
        return result;
    }

    private OrderCardVO fromVenueOrder(VenueOrder order) {
        String meta = "%s %s-%s".formatted(
                order.getServiceDate(),
                order.getStartTime().toString().substring(0, 5),
                order.getEndTime().toString().substring(0, 5));
        return new OrderCardVO(
                "venue-" + order.getId(),
                order.getProductTitle(),
                order.getVenueName(),
                meta,
                order.getAmount(),
                order.getStatus(),
                order.getVerifyCode());
    }

    private OrderCardVO fromEquipmentOrder(OrderSummary order) {
        return new OrderCardVO(
                "equipment-" + order.getId(),
                equipmentTitle(order),
                order.getAddress(),
                "%s · 共 %d 件".formatted(DATE_TIME.format(order.getCreatedAt()), equipmentQuantity(order)),
                order.getTotalAmount(),
                order.getStatus(),
                order.getOrderNo());
    }

    private OrderCardVO fromSeckillOrder(SeckillOrder order, String subtitle) {
        return new OrderCardVO(
                "seckill-" + order.getType() + "-" + order.getId(),
                order.getProductName(),
                subtitle,
                DATE_TIME.format(order.getCreatedAt()),
                order.getAmount(),
                order.getStatus(),
                "#" + order.getId());
    }

    private String equipmentTitle(OrderSummary order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "装备订单";
        }
        String firstName = order.getItems().getFirst().getProductName();
        if (order.getItems().size() == 1) {
            return firstName;
        }
        return firstName + " 等 " + order.getItems().size() + " 件";
    }

    private int equipmentQuantity(OrderSummary order) {
        if (order.getItems() == null) {
            return 0;
        }
        return order.getItems().stream()
                .map(OrderSummary.OrderItem::getQuantity)
                .filter(quantity -> quantity != null && quantity > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }
}
