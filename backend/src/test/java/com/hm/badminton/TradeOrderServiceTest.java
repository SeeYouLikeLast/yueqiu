package com.hm.badminton;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.trade.OrderReference;
import com.hm.badminton.dto.trade.PaymentRequest;
import com.hm.badminton.dto.trade.PaymentResult;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.IVenueItemService;
import com.hm.badminton.service.trade.impl.TradeOrderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeOrderServiceTest {

    @Test
    void cartCheckoutShouldNotRequireTradeTypeAndShouldReturnPublicOrderNumber() {
        IVenueItemService venueItemService = mock(IVenueItemService.class);
        IEquipmentService equipmentService = mock(IEquipmentService.class);
        TradeOrderService service = new TradeOrderService(venueItemService, equipmentService);
        String orderNo = "7fbc8c80-33cc-4c94-a4c2-e137ef7b27ac";

        when(venueItemService.cart(1L)).thenReturn(List.of());
        when(equipmentService.cart(1L)).thenReturn(List.of(new CartItem()));
        when(equipmentService.createOrder(eq(1L), any())).thenReturn(new OrderReference(2L, orderNo));

        PaymentRequest request = new PaymentRequest();
        request.setAddress("西安市 到店自提");
        PaymentResult result = service.payCart(1L, request);

        assertThat(result.getVenueOrderNos()).isEmpty();
        assertThat(result.getEquipmentOrderNo()).isEqualTo(orderNo);
        verify(equipmentService).pay(1L, 2L);
    }

    @Test
    void directPaymentShouldStillRequireTradeType() {
        TradeOrderService service = new TradeOrderService(
                mock(IVenueItemService.class),
                mock(IEquipmentService.class));

        assertThatThrownBy(() -> service.payDirect(1L, new PaymentRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务类型");
    }
}
