package com.hm.badminton.controller.place;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.AmapPlace;
import com.hm.badminton.service.place.IAmapPlaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/places")
public class PlaceController {

    private final IAmapPlaceService amapPlaceService;

    public PlaceController(IAmapPlaceService amapPlaceService) {
        this.amapPlaceService = amapPlaceService;
    }

    @GetMapping("/nearby")
    public ApiResponse<PageResult<AmapPlace>> nearby(@RequestParam(required = false) String sport,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String city,
                                                     @RequestParam Double lng,
                                                     @RequestParam Double lat,
                                                     @RequestParam(required = false) Integer radius,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(amapPlaceService.nearby(sport, keyword, city, lng, lat, radius, page, size));
    }

    @GetMapping("/regeo")
    public ApiResponse<Map<String, Object>> reverseGeocode(@RequestParam Double lng,
                                                           @RequestParam Double lat) {
        return ApiResponse.ok(amapPlaceService.reverseGeocode(lng, lat));
    }
}



