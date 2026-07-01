package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.Venue;
import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.service.IVenueService;
import com.hm.badminton.service.impl.VenueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/venues")
public class VenueController {

    private final IVenueService venueService;
    private final UserContext userContext;

    public VenueController(IVenueService venueService, UserContext userContext) {
        this.venueService = venueService;
        this.userContext = userContext;
    }

    @GetMapping
    public ApiResponse<PageResult<Venue>> list(@RequestParam(required = false) String city,
                                               @RequestParam(required = false) String area,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Double lng,
                                               @RequestParam(required = false) Double lat,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(venueService.list(city, area, keyword, lng, lat, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Venue> detail(@PathVariable Long id) {
        return ApiResponse.ok(venueService.detail(id));
    }

    @GetMapping("/{id}/courts")
    public ApiResponse<List<Map<String, Object>>> courts(@PathVariable Long id) {
        return ApiResponse.ok(venueService.courts(id));
    }

    @GetMapping("/{id}/slots")
    public ApiResponse<List<Map<String, Object>>> slots(@PathVariable Long id) {
        return ApiResponse.ok(venueService.timeSlots(id));
    }

    @GetMapping("/{id}/reviews")
    public ApiResponse<List<VenueReview>> reviews(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(venueService.reviews(id, page, size));
    }

    @PostMapping("/reviews")
    public ApiResponse<VenueReview> createReview(@Valid @RequestBody VenueService.ReviewRequest request) {
        return ApiResponse.ok(venueService.createReview(userContext.requireUserId(), request));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Boolean>> favorite(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("favorited", venueService.toggleFavorite(userContext.requireUserId(), id)));
    }

    @GetMapping("/favorites/me")
    public ApiResponse<List<Venue>> myFavorites() {
        return ApiResponse.ok(venueService.favorites(userContext.requireUserId()));
    }
}

