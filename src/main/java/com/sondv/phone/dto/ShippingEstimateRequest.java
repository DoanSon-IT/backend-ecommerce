package com.sondv.phone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShippingEstimateRequest {
    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    @NotBlank(message = "Nhà vận chuyển không được để trống")
    private String carrier;
}