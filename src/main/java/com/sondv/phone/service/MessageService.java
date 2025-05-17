package com.sondv.phone.service;

import com.sondv.phone.entity.Message;
import com.sondv.phone.entity.User;
import com.sondv.phone.repository.MessageRepository;
import com.sondv.phone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public Message saveMessage(Long senderId, Long receiverId, String content) {
        if (receiverId == null || content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Receiver ID hoặc nội dung không hợp lệ.");
        }

        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setRead(false);
        return messageRepository.save(message);
    }

    // MessageService.java
    public List<Message> getChatHistory(Long customerId) {
        Long adminId = 0L;
        List<Message> history = messageRepository.findConversation(customerId, adminId);
        System.out.println("📜 Lịch sử tin nhắn cho customerId=" + customerId + ": " + history);

        // Lấy avatarUrl từ userRepository
        history.forEach(msg -> {
            if (msg.getSenderId() != 0) { // Chỉ lấy avatar cho khách hàng, không lấy cho admin (senderId = 0)
                User user = userRepository.findById(msg.getSenderId())
                        .orElseThrow(
                                () -> new IllegalArgumentException("User không tồn tại với ID: " + msg.getSenderId()));
                msg.setSenderAvatarUrl(user.getAvatarUrl());
            }
        });

        return history;
    }

    public void markAsRead(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Tin nhắn không tồn tại với ID: " + messageId));
        if (!message.isRead()) {
            message.setRead(true);
            messageRepository.save(message);
        }
    }

    public List<User> getAllUsersWithMessages() {
        // Lấy tất cả senderId và receiverId từng xuất hiện trong bảng tin nhắn
        List<Long> ids = messageRepository.findDistinctUserIdsInMessages();

        // Lọc những user thực sự tồn tại trong hệ thống
        return userRepository.findAllById(ids);
    }

    public Map<Long, Long> getUnreadMessageCount() {
        List<User> users = getAllUsersWithMessages();
        Map<Long, Long> unreadCount = new HashMap<>();
        for (User user : users) {
            Long count = messageRepository.countBySenderIdAndReceiverIdAndIsReadFalse(user.getId(), 0L);
            unreadCount.put(user.getId(), count);
        }
        return unreadCount;
    }

    public void markConversationAsRead(Long customerId) {
        List<Message> messages = messageRepository.findConversation(customerId, 0L);
        messages.forEach(msg -> {
            if (!msg.isRead() && msg.getSenderId().equals(customerId)) {
                msg.setRead(true);
                messageRepository.save(msg);
            }
        });
    }
}