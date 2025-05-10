package com.sondv.phone.service;

import com.sondv.phone.entity.Order;
import com.sondv.phone.entity.Payment;
import com.sondv.phone.entity.PaymentMethod;
import com.sondv.phone.entity.PaymentStatus;
import com.sondv.phone.entity.User;
import com.sondv.phone.repository.OrderRepository;
import com.sondv.phone.repository.PaymentRepository;
import com.sondv.phone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Transactional
    public Payment createPayment(Long orderId, PaymentMethod method) {
        // Kiểm tra xem order_id đã tồn tại trong bảng payments chưa
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            // Nếu thanh toán đã tồn tại và ở trạng thái PENDING, cho phép tiếp tục
            if (payment.getStatus() == PaymentStatus.PENDING) {
                log.info("Payment exists for orderId: {}, but status is PENDING, proceeding with update", orderId);
                payment.setPaymentMethod(method);
                payment.setCreatedAt(LocalDateTime.now());
                return paymentRepository.save(payment);
            }
            log.error("Payment already exists for orderId: {} with status: {}", orderId, payment.getStatus());
            throw new IllegalArgumentException("Thanh toán cho đơn hàng này đã tồn tại và không thể thay đổi");
        }

        Order order = getOrderById(orderId);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment updatePaymentStatus(Long orderId, PaymentStatus status, String transactionId) {
        Payment payment = getPaymentByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán!"));
        payment.setStatus(status);
        if (transactionId != null) {
            payment.setTransactionId(transactionId);
        }
        return paymentRepository.save(payment);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));
    }

    public Optional<Payment> getPaymentByOrderId(Long orderId) {
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isPresent()) {
            return paymentOpt;
        }

        log.warn("Payment not found for orderId: {}. Creating default payment record.", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        return Optional.of(payment);
    }

    public Optional<Payment> getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId);
    }

    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán!"));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
    }
}