package com.sondv.phone.dto;

import lombok.Data;

@Data
public class ReviewResponseDto {
    private Long id;
    private int rating;
    private String comment;
    private String createdAt;

    private String productName;
    private String customerName;
}
