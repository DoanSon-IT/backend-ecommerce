package com.sondv.phone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfitStatDTO {
    private String period;
    private BigDecimal totalProfit;
    private BigDecimal totalRevenue;
}
