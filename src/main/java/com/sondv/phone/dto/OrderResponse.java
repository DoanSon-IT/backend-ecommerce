package com.sondv.phone.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private Long id;
    private String status;
    private LocalDateTime createdAt;
    private BigDecimal totalPrice;
    private CustomerInfoDTO customer;
    private ShippingInfoDTO shippingInfo;
    private List<OrderDetailResponse> orderDetails;
}
