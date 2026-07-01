package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.service.IAdminService;
import com.hm.badminton.service.IEquipmentService;
import com.hm.badminton.service.IVenueService;
import com.hm.badminton.service.impl.EquipmentService;
import com.hm.badminton.service.impl.VenueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final IAdminService adminService;
    private final IVenueService venueService;
    private final IEquipmentService equipmentService;
    private final UserContext userContext;

    public AdminController(IAdminService adminService,
                           IVenueService venueService,
                           IEquipmentService equipmentService,
                           UserContext userContext) {
        this.adminService = adminService;
        this.venueService = venueService;
        this.equipmentService = equipmentService;
        this.userContext = userContext;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        userContext.require();
        return ApiResponse.ok(adminService.overview());
    }

    @GetMapping("/hot-venues")
    public ApiResponse<List<Map<String, Object>>> hotVenues() {
        userContext.require();
        return ApiResponse.ok(adminService.hotVenues());
    }

    @GetMapping("/hot-products")
    public ApiResponse<List<Map<String, Object>>> hotProducts() {
        userContext.require();
        return ApiResponse.ok(adminService.hotProducts());
    }

    @GetMapping("/latest-reviews")
    public ApiResponse<List<Map<String, Object>>> latestReviews() {
        userContext.require();
        return ApiResponse.ok(adminService.latestReviews());
    }

    @PostMapping("/venues")
    public ApiResponse<Map<String, Long>> createVenue(@Valid @RequestBody VenueService.VenueCreateRequest request) {
        userContext.require();
        return ApiResponse.ok(Map.of("venueId", venueService.createVenue(request)));
    }

    @PostMapping("/products")
    public ApiResponse<Map<String, Long>> createProduct(@Valid @RequestBody EquipmentService.ProductCreateRequest request) {
        userContext.require();
        return ApiResponse.ok(Map.of("productId", equipmentService.createProduct(request)));
    }
}

