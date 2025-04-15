package com.sondv.phone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopProductDTO {
    private Long productId;
    private String productName;
    private Long totalSold;
}

