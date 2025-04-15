package com.sondv.phone.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShippingInfoDTO {
    private String address;
    private String phoneNumber;
    private String carrier;
    private BigDecimal shippingFee;
    private LocalDateTime estimatedDelivery;
}
