package com.sondv.phone.dto;

import com.sondv.phone.entity.OrderStatus;
import lombok.Data;

@Data
public class ShippingStatusRequest {
    private OrderStatus status;
}