package com.sondv.phone.service;

import com.sondv.phone.entity.*;
import com.sondv.phone.repository.OrderRepository;
import com.sondv.phone.repository.PaymentRepository;
import com.sondv.phone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));
    }

    public Optional<Payment> getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán với ID: " + paymentId));
    }

    public Optional<Payment> getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId);
    }

    public Payment createPayment(Long orderId, PaymentMethod method) {
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            throw new IllegalStateException("Đơn hàng này đã có thanh toán.");
        }

        Payment payment = new Payment();
        payment.setOrder(orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng")));
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());

        return paymentRepository.save(payment);
    }

    public Payment updatePaymentStatus(Long orderId, PaymentStatus status, String transactionId) {
        Payment payment = getPaymentByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán!"));
        payment.setStatus(status);
        payment.setTransactionId(transactionId);
        return paymentRepository.save(payment);
    }
}