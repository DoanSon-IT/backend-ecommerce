package com.sondv.phone.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendEmail(String to, String subject, String content) {
        int retryCount = 3;

        for (int i = 0; i < retryCount; i++) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(content, true); // HTML mode

                mailSender.send(message);
                log.info("✅ Email đã được gửi đến: {}", to);
                return;
            } catch (MailException | MessagingException e) {
                log.warn("⚠️ Lần thử {} gửi email đến {} thất bại: {}", (i + 1), to, e.getMessage());
            }
        }

        log.error("❌ Gửi email thất bại sau nhiều lần thử đến: {}", to);
    }

    public String loadEmailTemplate(String fileName, Map<String, String> placeholders) {
        try (InputStream is = getClass().getResourceAsStream("/templates/email/" + fileName)) {
            if (is == null) {
                throw new RuntimeException("❌ Không tìm thấy file html: " + fileName);
            }

            String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return template;
        } catch (IOException e) {
            throw new RuntimeException("❌ Lỗi khi đọc html email: " + fileName, e);
        }
    }

}
