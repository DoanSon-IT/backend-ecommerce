package com.sondv.phone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sondv.phone.entity.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentRequest {
    private Long orderId;

    @JsonProperty("method")
    private PaymentMethod method;
}