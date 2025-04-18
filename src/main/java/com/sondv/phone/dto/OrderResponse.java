package com.sondv.phone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String status;
    private LocalDateTime createdAt;
    private BigDecimal totalPrice;
    private BigDecimal shippingFee;
    private CustomerInfoDTO customer;
    private ShippingInfoDTO shippingInfo;
    private List<OrderDetailResponse> orderDetails;
    private String paymentMethod;
}
