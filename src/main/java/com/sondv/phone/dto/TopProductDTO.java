package com.sondv.phone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TopProductDTO {
    private Long productId;
    private String productName;
    private String category;
    private Long totalSold;
    private BigDecimal revenue;
    private BigDecimal profit;
}
