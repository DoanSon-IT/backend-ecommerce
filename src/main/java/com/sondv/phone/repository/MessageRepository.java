package com.sondv.phone.repository;

import com.sondv.phone.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    // MessageRepository.java
    @Query("SELECT m FROM Message m WHERE (m.senderId = :id1 AND m.receiverId = :id2) OR (m.senderId = :id2 AND m.receiverId = :id1) ORDER BY m.timestamp ASC")
    List<Message> findConversation(@Param("id1") Long id1, @Param("id2") Long id2);

    long countBySenderId(Long senderId);
    @Query(value = "(SELECT DISTINCT sender_id FROM messages) " +
            "UNION " +
            "(SELECT DISTINCT receiver_id FROM messages)",
            nativeQuery = true)
    List<Long> findDistinctUserIdsInMessages();

    @Query("SELECT COUNT(m) FROM Message m WHERE m.senderId = :senderId AND m.receiverId = :receiverId AND m.isRead = false")
    Long countBySenderIdAndReceiverIdAndIsReadFalse(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId);
}