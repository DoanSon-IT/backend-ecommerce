package com.sondv.phone.controller;

import com.sondv.phone.dto.ChatRequest;
import com.sondv.phone.dto.ChatResponse;
import com.sondv.phone.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/ask")
    public ChatResponse askChatbot(@RequestBody ChatRequest request) {
        try {
            return chatbotService.processUserMessage(request.getUserId(), request.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi xử lý request: " + e.getMessage(), e);
        }
    }
}
