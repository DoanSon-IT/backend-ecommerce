package com.sondv.phone.dto;

import lombok.Data;
import java.util.List;

@Data
public class DiscountApplyRequest {
    private String discountCode;
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        private Long productId;
        private int quantity;
    }
}
