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
        String vnp_ResponseCode = params.get("vnp_ResponseCode");
        String vnp_TxnRef = params.get("vnp_TxnRef");

        if ("00".equals(vnp_ResponseCode)) {
            // Cập nhật trạng thái thanh toán sang PAID
            paymentService.updatePaymentStatus(Long.valueOf(vnp_TxnRef), PaymentStatus.PAID, vnp_TxnRef);
            // Redirect đến trang thành công
            response.sendRedirect("/order-confirmation?txnRef=" + vnp_TxnRef);
        } else {
            // Redirect đến trang lỗi
            response.sendRedirect("/payment-failed?code=" + vnp_ResponseCode);
        }
    }

    @PostMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> handleVNPayIPN(@RequestParam Map<String, String> params) {
        // Xác thực hash
        String vnp_SecureHash = params.get("vnp_SecureHash");
        String calculatedHash = vnPayService.calculateSecureHash(params);
        if (!calculatedHash.equals(vnp_SecureHash)) {
            log.error("Invalid secure hash");
            Map<String, String> response = new HashMap<>();
            response.put("Rspcode", "01");
            response.put("Message", "Invalid secure hash");
            return ResponseEntity.ok(response);
        }

        // Lấy thông tin giao dịch
        String vnp_TxnRef = params.get("vnp_TxnRef");
        String vnp_ResponseCode = params.get("vnp_ResponseCode");

        // Chuyển đổi vnp_TxnRef từ String sang Long (vì đây là paymentId)
        Long paymentId;
        try {
            paymentId = Long.parseLong(vnp_TxnRef);
        } catch (NumberFormatException e) {
            log.error("Invalid transaction reference: {}", vnp_TxnRef);
            Map<String, String> response = new HashMap<>();
            response.put("Rspcode", "01");
            response.put("Message", "Invalid transaction reference");
            return ResponseEntity.ok(response);
        }

        // Lấy đối tượng Payment từ paymentId
        Payment payment = paymentService.getPaymentById(paymentId);
        if (payment == null) {
            log.error("Payment not found for id: {}", paymentId);
            Map<String, String> response = new HashMap<>();
            response.put("Rspcode", "01");
            response.put("Message", "Payment not found");
            return ResponseEntity.ok(response);
        }

        // Lấy orderId từ đối tượng Payment
        Long orderId = payment.getOrder().getId();

        // Xác định trạng thái thanh toán
        PaymentStatus status = "00".equals(vnp_ResponseCode) ? PaymentStatus.PAID : PaymentStatus.FAILED;

        // Cập nhật trạng thái thanh toán
        paymentService.updatePaymentStatus(orderId, status, vnp_TxnRef);

        // Trả về phản hồi JSON
        Map<String, String> response = new HashMap<>();
        response.put("Rspcode", "00");
        response.put("Message", "success");
        return ResponseEntity.ok(response);
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