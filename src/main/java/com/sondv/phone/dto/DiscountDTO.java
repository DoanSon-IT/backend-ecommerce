package com.sondv.phone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountDTO {
    private String code;
    private double discountPercentage;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private double minOrderValue;
    private int probabilityWeight;
}