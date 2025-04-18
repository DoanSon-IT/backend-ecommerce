package com.sondv.phone.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShippingEstimateDTO {
    private BigDecimal fee;
    private LocalDateTime estimatedDelivery;
}