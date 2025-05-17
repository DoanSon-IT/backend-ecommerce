package com.sondv.phone.controller;

import com.sondv.phone.entity.Message;
import com.sondv.phone.entity.User;
import com.sondv.phone.repository.UserRepository;
import com.sondv.phone.security.JwtUtil;
import com.sondv.phone.service.CloudinaryService;
import com.sondv.phone.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// ChatController.java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final CloudinaryService cloudinaryService; // Thêm

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<User> getAllUsersWithMessages() {
        return messageService.getAllUsersWithMessages();
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<Message> getChatHistoryForAdmin(@RequestParam Long customerId) {
        return messageService.getChatHistory(customerId);
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('STAFF')")
    public List<Message> getChatHistoryForUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            if (principal instanceof User user) {
                System.out.println("✅ Đã xác thực user từ cookie: " + user.getEmail());
                return messageService.getChatHistory(user.getId());
            } else {
                System.err.println("❌ Không tìm được user từ SecurityContext!");
                return List.of();
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi lấy lịch sử chat từ SecurityContext: " + e.getMessage());
            return List.of();
        }
    }

    @PostMapping("/mark-as-read")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public void markAsRead(@RequestParam Long messageId) {
        messageService.markAsRead(messageId);
    }

    @PostMapping("/mark-conversation-as-read")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public void markConversationAsRead(@RequestParam Long customerId) {
        messageService.markConversationAsRead(customerId);
    }

    private String extractEmailFromToken(String token) {
        return jwtUtil.extractUsername(token);
    }

    @PostMapping("/send-to-customer")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Message sendToCustomer(@RequestParam Long receiverId, @RequestParam String message) {
        System.out.println("✅ Gửi tới ID: " + receiverId + " | Nội dung: " + message);
        messageService.markConversationAsRead(receiverId);
        return messageService.saveMessage(0L, receiverId, message);
    }

    @PostMapping("/send-to-agent")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('STAFF')")
    public Message sendToAgent(@RequestParam String message) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            System.out.println("✅ Người dùng gửi tin nhắn: email=" + user.getEmail() + ", id=" + user.getId());
            return messageService.saveMessage(user.getId(), 0L, message);
        } else {
            System.err.println("❌ Không xác thực được người dùng: " + principal);
            throw new IllegalStateException("Không xác thực được người dùng!");
        }
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Map<Long, Long> getUnreadMessageCount() {
        return messageService.getUnreadMessageCount();
    }

    @PostMapping("/upload-image")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public String uploadImage(@RequestParam("image") MultipartFile file) throws IOException {
        String imageUrl = cloudinaryService.uploadImageToCloudinary(file);
        if (imageUrl == null) {
            throw new IllegalStateException("Không thể upload ảnh lên Cloudinary!");
        }
        return imageUrl;
    }
}