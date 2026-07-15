package com.hm.badminton.service.place;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.AmapPlace;

import java.util.Map;

public interface IAmapPlaceService {
    PageResult<AmapPlace> nearby(String sportCode,
                                 String keyword,
                                 String city,
                                 Double lng,
                                 Double lat,
                                 Integer radius,
                                 int page,
                                 int size);

    Map<String, Object> reverseGeocode(Double lng, Double lat);

    Map<String, Object> locateByIp(String clientIp);
}


