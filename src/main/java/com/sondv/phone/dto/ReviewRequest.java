package com.sondv.phone.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ReviewRequest {
    private Long orderDetailId;

    @Min(1)
    @Max(5)
    private int rating;

    private String comment;
}
