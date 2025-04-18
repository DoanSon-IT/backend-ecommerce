package com.sondv.phone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sondv.phone.entity.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentRequest {
    private Long orderId;

    @JsonProperty("paymentMethod")
    private PaymentMethod method;
}
