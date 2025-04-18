package com.sondv.phone.controller;

import com.sondv.phone.dto.PaymentRequest;
import com.sondv.phone.dto.PaymentUpdateRequest;
import com.sondv.phone.entity.*;
import com.sondv.phone.service.MomoService;
import com.sondv.phone.service.PaymentService;
import com.sondv.phone.service.VNPayService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final VNPayService vnPayService;
    private final MomoService momoService;
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping
    public ResponseEntity<Map<String, String>> createPayment(@RequestBody PaymentRequest paymentRequest, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        Order order = paymentService.getOrderById(paymentRequest.getOrderId());

        if (!order.getCustomer().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        Payment payment = paymentService.createPayment(order.getId(), paymentRequest.getMethod());
        String paymentUrl;

        switch (paymentRequest.getMethod()) {
            case VNPAY:
                paymentUrl = vnPayService.createVNPayPayment(payment.getId(), order.getTotalPrice().doubleValue());
                break;
            case MOMO:
                paymentUrl = momoService.createMomoPayment(payment.getId(), order.getTotalPrice().doubleValue());
                break;
            case COD:
                paymentService.updatePaymentStatus(order.getId(), PaymentStatus.AWAITING_DELIVERY, null);
                paymentUrl = "/order-confirmation";
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("paymentUrl", paymentUrl);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay-return")
    public void handleVNPayCallback(@RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
        try {
            log.info("VNPay callback received: {}", params);

            String vnp_TxnRef = params.get("vnp_TxnRef"); // chính là paymentId
            String vnp_ResponseCode = params.get("vnp_ResponseCode");
            String vnp_SecureHash = params.get("vnp_SecureHash");

            // ✅ Bước 1: Xác thực hash nếu cần (bạn đã có calculateSecureHash)
            String calculatedHash = vnPayService.calculateSecureHash(params);
            if (!calculatedHash.equals(vnp_SecureHash)) {
                log.error("Invalid secure hash for payment {}", vnp_TxnRef);
                response.sendRedirect("/payment-failed");
                return;
            }

            // ✅ Bước 2: Truy payment theo vnp_TxnRef
            Long paymentId = Long.parseLong(vnp_TxnRef);
            Payment payment = paymentService.getPaymentById(paymentId);

            // ✅ Bước 3: Truy ngược về đơn hàng
            Order order = payment.getOrder();

            // ✅ Bước 4: Cập nhật trạng thái thanh toán
            if ("00".equals(vnp_ResponseCode)) {
                paymentService.updatePaymentStatus(order.getId(), PaymentStatus.PAID, vnp_TxnRef);
                response.sendRedirect("/order-success"); // URL tùy bạn định nghĩa
            } else {
                paymentService.updatePaymentStatus(order.getId(), PaymentStatus.FAILED, vnp_TxnRef);
                response.sendRedirect("/payment-failed");
            }

        } catch (Exception e) {
            log.error("Error processing VNPay callback: {}", e.getMessage());
            response.sendRedirect("/payment-error");
        }
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @GetMapping("/url/{orderId}")
    public ResponseEntity<Map<String, String>> getPaymentUrl(@PathVariable Long orderId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        Order order = paymentService.getOrderById(orderId);
        Payment payment = paymentService.getPaymentByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán!"));

        if (!order.getCustomer().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        String paymentUrl;
        switch (payment.getPaymentMethod()) {
            case VNPAY:
                paymentUrl = vnPayService.createVNPayPayment(payment.getId(), order.getTotalPrice().doubleValue());
                break;
            case MOMO:
                paymentUrl = momoService.createMomoPayment(payment.getId(), order.getTotalPrice().doubleValue());
                break;
            case COD:
                paymentUrl = "/order-confirmation";
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("paymentUrl", paymentUrl);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CUSTOMER') or hasAuthority('ADMIN')")
    @GetMapping("/{orderId}")
    public ResponseEntity<Payment> getPayment(@PathVariable Long orderId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(null);
        }

        String email = authentication.getName();
        if (email == null || email.isEmpty()) {
            return ResponseEntity.status(400).body(null);
        }

        try {
            User user = paymentService.getUserByEmail(email);
            Payment payment = paymentService.getPaymentByOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán!"));

            if (!payment.getOrder().getCustomer().getUser().getEmail().equals(email) &&
                    !user.getRoles().contains("ADMIN")) {
                return ResponseEntity.status(403).body(null);
            }

            return ResponseEntity.ok(payment);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(null);
        }
    }
    @PreAuthorize("hasAuthority('CUSTOMER') or hasAuthority('ADMIN')")
    @GetMapping("/by-transaction/{txnRef}")
    public ResponseEntity<Payment> getPaymentByTransactionId(@PathVariable String txnRef, Authentication authentication) {
        log.info("Fetching payment for transactionId: {}", txnRef);
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthenticated request for transactionId: {}", txnRef);
            return ResponseEntity.status(401).body(null);
        }

        String email = authentication.getName();
        if (email == null || email.isEmpty()) {
            log.error("Empty email for transactionId: {}", txnRef);
            return ResponseEntity.status(400).body(null);
        }

        try {
            User user = paymentService.getUserByEmail(email);
            Payment payment = paymentService.getPaymentByTransactionId(txnRef)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán!"));
            log.info("Found payment: {}", payment);

            if (!payment.getOrder().getCustomer().getUser().getEmail().equals(email) &&
                    !user.getRoles().contains("ADMIN")) {
                log.error("Forbidden access for transactionId: {} by email: {}", txnRef, email);
                return ResponseEntity.status(403).body(null);
            }

            return ResponseEntity.ok(payment);
        } catch (RuntimeException e) {
            log.error("Error fetching payment for transactionId: {} - {}", txnRef, e.getMessage());
            return ResponseEntity.status(404).body(null);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/{orderId}")
    public ResponseEntity<Payment> updatePaymentStatus(@PathVariable Long orderId,
                                                       @RequestBody PaymentUpdateRequest paymentUpdateRequest) {
        return ResponseEntity.ok(paymentService.updatePaymentStatus(
                orderId,
                paymentUpdateRequest.getStatus(),
                paymentUpdateRequest.getTransactionId()));
    }

    private String buildHashData(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            String value = params.get(fieldName);
            if (value != null && !value.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                if (fieldNames.indexOf(fieldName) < fieldNames.size() - 1) {
                    hashData.append('&');
                }
            }
        }
        return hashData.toString();
    }
}