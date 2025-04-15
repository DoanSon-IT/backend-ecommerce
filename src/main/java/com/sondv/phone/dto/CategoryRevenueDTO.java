package com.sondv.phone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class CategoryRevenueDTO {
    private String category;
    private BigDecimal totalRevenue;
    private Long orderCount;
    private Long productCount;
}
