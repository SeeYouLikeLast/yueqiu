package com.hm.badminton.controller.place;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.AmapPlace;
import com.hm.badminton.service.place.IAmapPlaceService;
import com.hm.badminton.utils.ClientIpUtils;
import com.hm.badminton.utils.LocationContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 高德附近场所、逆地理编码和 IP 定位入口。 */
@RestController
@RequestMapping("/places")
public class PlaceController {

    private final IAmapPlaceService amapPlaceService;
    private final LocationContextResolver locationContextResolver;

    public PlaceController(IAmapPlaceService amapPlaceService, LocationContextResolver locationContextResolver) {
        this.amapPlaceService = amapPlaceService;
        this.locationContextResolver = locationContextResolver;
    }

    @GetMapping("/nearby")
    public ApiResponse<PageResult<AmapPlace>> nearby(@RequestParam(required = false) String sport,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestHeader(value = "X-Location-City", required = false) String locationCity,
                                                     @RequestParam Double lng,
                                                     @RequestParam Double lat,
                                                     @RequestParam(required = false) Integer radius,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(amapPlaceService.nearby(
                sport, keyword, locationContextResolver.resolveCity(locationCity), lng, lat, radius, page, size));
    }

    @GetMapping("/regeo")
    public ApiResponse<Map<String, Object>> reverseGeocode(@RequestParam Double lng,
                                                           @RequestParam Double lat) {
        return ApiResponse.ok(amapPlaceService.reverseGeocode(lng, lat));
    }

    /**
     * 精确浏览器定位不可用时的城市级兜底。
     * 后端仅信任由本机 Nginx 写入的转发头，并将解析出的客户端 IP 交给高德。
     */
    @GetMapping("/ip-location")
    public ApiResponse<Map<String, Object>> ipLocation(HttpServletRequest request) {
        return ApiResponse.ok(amapPlaceService.locateByIp(ClientIpUtils.resolve(request)));
    }
}



