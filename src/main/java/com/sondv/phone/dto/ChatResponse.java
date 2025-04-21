package com.sondv.phone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChatResponse {
    private String reply;
    private List<Long> productIds;

    public ChatResponse(String reply) {
        this.reply = reply;
        this.productIds = null;
    }
}
