package com.sondv.phone.dto;

import lombok.Data;

@Data
public class ReviewRequestDto {
    private Long orderDetailId;
    private int rating;
    private String comment;
}
