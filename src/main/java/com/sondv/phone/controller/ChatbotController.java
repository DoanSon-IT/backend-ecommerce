package com.sondv.phone.controller;

import com.sondv.phone.dto.ChatRequest;
import com.sondv.phone.dto.ChatResponse;
import com.sondv.phone.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/ask")
    public ChatResponse askChatbot(@RequestBody ChatRequest request) {
        return chatbotService.processUserMessage(request.getUserId(), request.getMessage());
    }
}
