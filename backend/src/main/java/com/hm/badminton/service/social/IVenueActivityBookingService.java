package com.hm.badminton.service.social;

import com.hm.badminton.dto.social.VenueActivityBookingRequest;
import com.hm.badminton.dto.social.VenueActivityBookingResult;

/** 场馆时段购买和约球活动创建的业务闭环。 */
public interface IVenueActivityBookingService {
    VenueActivityBookingResult bookAndCreateActivity(Long userId, VenueActivityBookingRequest request);
}
