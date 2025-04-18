package com.sondv.phone.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiClient {

    @Value("${GROQ_API_KEY}")
    private String groqApiKey;

    @Value("${GROQ_API_KEY}")
    private String groqApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String ask(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey); // ✅ Gắn Groq API key

        Map<String, Object> requestBody = Map.of(
                "entity", "llama3-70b-8192",
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Bạn là một trợ lý tư vấn bán điện thoại thông minh. Trả lời thân thiện, dễ hiểu, ngắn gọn. Có thể dùng emoji nếu cần."),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 200,
                "temperature", 0.8
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(groqApiUrl, request, Map.class);
            Map body = response.getBody();

            if (body == null || body.get("choices") == null) {
                System.err.println("⚠️ Groq trả về sai format: " + response);
                return fallbackAnswer(prompt);
            }

            List<Map> choices = (List<Map>) body.get("choices");
            Map message = (Map) choices.get(0).get("message");
            return message.get("content").toString().trim();

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi gọi Groq: " + e.getMessage());
            return fallbackAnswer(prompt);
        }
    }

    private String fallbackAnswer(String prompt) {
        return "Hiện tại trợ lý đang quá tải. Anh/chị vui lòng thử lại sau ạ 🙏";
    }
}
