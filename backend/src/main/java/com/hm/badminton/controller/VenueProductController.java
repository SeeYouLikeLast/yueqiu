package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.entity.VenueProduct;
import com.hm.badminton.entity.VenueProductInventory;
import com.hm.badminton.service.IVenueProductService;
import com.hm.badminton.service.impl.VenueProductService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class VenueProductController {

    private final IVenueProductService venueProductService;
    private final UserContext userContext;

    public VenueProductController(IVenueProductService venueProductService, UserContext userContext) {
        this.venueProductService = venueProductService;
        this.userContext = userContext;
    }

    @GetMapping("/venue-products")
    public ApiResponse<List<VenueProduct>> products(@RequestParam(required = false) String sport,
                                                    @RequestParam(required = false) String amapPlaceId,
                                                    @RequestParam(required = false) Long venueId,
                                                    @RequestParam(required = false) Integer placeRank,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(venueProductService.products(sport, amapPlaceId, venueId, placeRank, limit));
    }

    @GetMapping("/venue-products/sales")
    public ApiResponse<PageResult<VenueProduct>> saleProducts(@RequestParam(required = false) String sport,
                                                              @RequestParam(required = false) String productType,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(venueProductService.saleProducts(sport, productType, keyword, page, size));
    }

    @GetMapping("/venue-products/{id}")
    public ApiResponse<VenueProduct> detail(@PathVariable Long id) {
        return ApiResponse.ok(venueProductService.detail(id));
    }

    @GetMapping("/venue-products/{id}/inventories")
    public ApiResponse<List<VenueProductInventory>> inventories(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(venueProductService.inventories(id, date));
    }

    @PostMapping("/venue/orders")
    public ApiResponse<Map<String, Object>> createOrder(@Valid @RequestBody VenueProductService.OrderCreateRequest request) {
        VenueOrder order = venueProductService.createOrder(userContext.requireUserId(), request);
        return ApiResponse.ok(Map.of("orderId", order.id(), "verifyCode", order.verifyCode(), "order", order));
    }

    @PostMapping("/venue-products/{id}/quick-pay")
    public ApiResponse<VenueOrder> quickPay(@PathVariable Long id,
                                            @RequestBody(required = false) VenueProductService.VenueContextRequest request) {
        return ApiResponse.ok(venueProductService.quickPay(userContext.requireUserId(), id, request));
    }

    @PostMapping("/venue/orders/{id}/pay")
    public ApiResponse<VenueOrder> pay(@PathVariable Long id) {
        return ApiResponse.ok(venueProductService.pay(userContext.requireUserId(), id));
    }

    @GetMapping("/venue/orders")
    public ApiResponse<List<VenueOrder>> myOrders() {
        return ApiResponse.ok(venueProductService.myOrders(userContext.requireUserId()));
    }
}

