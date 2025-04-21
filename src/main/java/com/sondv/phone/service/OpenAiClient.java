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

    @Value("${GROQ_API_URL}")
    private String groqApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String ask(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey); // ✅ Gắn Groq API key

        Map<String, Object> requestBody = Map.of(
                "model", "llama3-70b-8192",
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Bạn là trợ lý tư vấn bán điện thoại. QUAN TRỌNG: CHỈ sử dụng thông tin được cung cấp trong prompt, " +
                                        "KHÔNG ĐƯỢC bịa ra thông tin. Nếu không có thông tin, hãy trả lời rằng bạn chưa có dữ liệu về sản phẩm đó. " +
                                        "Luôn trung thực và rõ ràng về những gì bạn biết và không biết."),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 150,
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
