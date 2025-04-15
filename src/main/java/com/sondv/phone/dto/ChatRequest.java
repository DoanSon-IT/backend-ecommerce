package com.sondv.phone.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long userId;
    private String message;
}
