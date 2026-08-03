package com.hm.badminton.dto.social;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 订单支付与活动创建都成功后返回的结果。 */
@Data
@AllArgsConstructor
public class VenueActivityBookingResult {
    private String venueOrderNo;
    private String verifyCode;
    private Long activityId;
}
