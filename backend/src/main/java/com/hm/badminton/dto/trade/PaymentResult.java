package com.hm.badminton.dto.trade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Public payment result. Internal numeric primary keys are intentionally not exposed. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {
    private String orderNo;
    private String verifyCode;
    private List<String> venueOrderNos = new ArrayList<>();
    private String equipmentOrderNo;

    public static PaymentResult direct(String orderNo, String verifyCode) {
        return new PaymentResult(orderNo, verifyCode, new ArrayList<>(), null);
    }

    public static PaymentResult cart(List<String> venueOrderNos, String equipmentOrderNo) {
        return new PaymentResult(null, null, new ArrayList<>(venueOrderNos), equipmentOrderNo);
    }
}
