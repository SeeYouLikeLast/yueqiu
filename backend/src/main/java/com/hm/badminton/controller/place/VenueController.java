package com.hm.badminton.controller.place;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.service.place.IVenueService;
import com.hm.badminton.utils.LocationContextResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/venues")
public class VenueController {

    private final IVenueService venueService;
    private final LocationContextResolver locationContextResolver;

    public VenueController(IVenueService venueService, LocationContextResolver locationContextResolver) {
        this.venueService = venueService;
        this.locationContextResolver = locationContextResolver;
    }

    /**
     * 以当前城市上下文和高德场所顺序查询评价。
     * 城市从 X-Location-City、登录 Token 或默认城市依次解析，避免城市出现在查询串中。
     */
    @GetMapping("/reviews/{sport}/{placeRank}")
    public ApiResponse<List<VenueReview>> reviewsByPlaceSlot(@PathVariable String sport,
                                                             @PathVariable int placeRank,
                                                             @RequestHeader(value = "X-Location-City", required = false) String locationCity,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.ok(venueService.reviewsByPlaceSlot(
                locationContextResolver.resolveCity(locationCity), sport, placeRank, page, size));
    }

}



