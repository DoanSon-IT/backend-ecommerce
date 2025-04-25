package com.sondv.phone.controller;

import com.sondv.phone.dto.PaymentRequest;
import com.sondv.phone.dto.PaymentUpdateRequest;
import com.sondv.phone.repository.PaymentRepository;
import com.sondv.phone.entity.*;
import com.sondv.phone.service.MomoService;
import com.sondv.phone.service.OrderService;
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
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping
    public ResponseEntity<Map<String, String>> createPayment(@RequestBody PaymentRequest paymentRequest, Authentication authentication) {
        if (paymentRequest.getMethod() == null) {
            log.error("Payment method is null in request: {}", paymentRequest);
            return ResponseEntity.badRequest().body(Map.of("message", "Phương thức thanh toán không được để trống"));
        }

        User user = (User) authentication.getPrincipal();
        Order order = paymentService.getOrderById(paymentRequest.getOrderId());

        if (!order.getCustomer().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        try {
            log.info("Creating payment for orderId: {}, method: {}", paymentRequest.getOrderId(), paymentRequest.getMethod());
            Payment payment = paymentService.createPayment(order.getId(), paymentRequest.getMethod());
            String paymentUrl;

            switch (paymentRequest.getMethod()) {
                case VNPAY:
                    paymentService.updatePaymentStatus(order.getId(), PaymentStatus.PROCESSING, null);
                    paymentUrl = vnPayService.createPayment(payment.getId(), order.getTotalPrice().doubleValue());
                    break;
                case COD:
                    paymentService.updatePaymentStatus(order.getId(), PaymentStatus.AWAITING_DELIVERY, payment.getTransactionId());
                    orderService.confirmOrder(order.getId(), user);
                    paymentUrl = "/order-confirmation";
                    break;
                case MOMO:
                    return ResponseEntity.badRequest().body(Map.of("message", "Phương thức Momo chưa được hỗ trợ"));
                default:
                    return ResponseEntity.badRequest().body(Map.of("message", "Phương thức thanh toán không hợp lệ"));
            }

            Map<String, String> response = new HashMap<>();
            response.put("paymentUrl", paymentUrl);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Error creating payment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/vnpay-return")
    @PreAuthorize("hasAuthority('CUSTOMER') or hasAuthority('ADMIN')")
    public void handleVNPayCallback(@RequestParam Map<String, String> params, HttpServletResponse response, Authentication authentication) throws IOException {
        String vnp_ResponseCode = params.get("vnp_ResponseCode");
        String vnp_TxnRef = params.get("vnp_TxnRef");
        String vnp_TransactionNo = params.get("vnp_TransactionNo");
        String vnp_SecureHash = params.get("vnp_SecureHash");

        log.info("VNPay callback received: TxnRef={}, ResponseCode={}, TransactionNo={}", vnp_TxnRef, vnp_ResponseCode, vnp_TransactionNo);

        if (!vnPayService.validateCallback(params)) {
            log.error("Invalid secure hash for TxnRef={}. Possible configuration issue with VNPay secret key.", vnp_TxnRef);
            response.sendRedirect("/payment-failed?code=99&message=invalid_hash");
            return;
        }

        try {
            Long paymentId = Long.valueOf(vnp_TxnRef);
            Payment payment = paymentService.getPaymentById(paymentId);

            User user = (User) authentication.getPrincipal();
            if (!payment.getOrder().getCustomer().getUser().getId().equals(user.getId()) &&
                    !user.getRoles().contains("ADMIN")) {
                log.error("User {} attempted to access payment {} without permission", user.getId(), paymentId);
                response.sendRedirect("/payment-failed?code=403&message=unauthorized");
                return;
            }

            // Kiểm tra trạng thái hiện tại để xử lý trùng lặp
            if (payment.getTransactionId() != null && payment.getTransactionId().equals(vnp_TransactionNo)) {
                log.warn("Duplicate transaction for TxnRef={}, TransactionNo={}. Current status: {}", vnp_TxnRef, vnp_TransactionNo, payment.getStatus());
                if (payment.getStatus() == PaymentStatus.PAID) {
                    response.sendRedirect("/order-confirmation");
                } else {
                    response.sendRedirect("/payment-failed?code=99&message=duplicate_transaction");
                }
                return;
            }

            PaymentStatus status = "00".equals(vnp_ResponseCode) ? PaymentStatus.PAID : PaymentStatus.FAILED;
            log.info("Updating payment status for TxnRef={} to {}", vnp_TxnRef, status);
            paymentService.updatePaymentStatus(payment.getOrder().getId(), status, vnp_TransactionNo);

            if (status == PaymentStatus.PAID) {
                log.info("Confirming order for payment TxnRef={}", vnp_TxnRef);
                orderService.confirmOrder(payment.getOrder().getId(), user);
                response.sendRedirect("/order-confirmation");
            } else {
                log.warn("Payment failed for TxnRef={}. ResponseCode={}", vnp_TxnRef, vnp_ResponseCode);
                response.sendRedirect("/payment-failed?code=" + vnp_ResponseCode + "&message=payment_failed");
            }
        } catch (NumberFormatException e) {
            log.error("Invalid paymentId format for TxnRef={}: {}", vnp_TxnRef, e.getMessage());
            response.sendRedirect("/payment-failed?code=99&message=invalid_payment_id");
        } catch (RuntimeException e) {
            log.error("Payment not found for TxnRef={}: {}", vnp_TxnRef, e.getMessage());
            response.sendRedirect("/payment-failed?code=99&message=payment_not_found");
        } catch (Exception e) {
            log.error("Error processing VNPay callback for TxnRef={}: {}", vnp_TxnRef, e.getMessage());
            response.sendRedirect("/payment-failed?code=99&message=processing_error");
        }
    }

    @PostMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> handleVNPayIPN(@RequestParam Map<String, String> params) {
        String vnp_TxnRef = params.get("vnp_TxnRef");
        String vnp_TransactionNo = params.get("vnp_TransactionNo");
        String vnp_ResponseCode = params.get("vnp_ResponseCode");

        log.info("VNPay IPN received: TxnRef={}, ResponseCode={}, TransactionNo={}", vnp_TxnRef, vnp_ResponseCode, vnp_TransactionNo);

        if (!vnPayService.validateCallback(params)) {
            log.error("Invalid secure hash for TxnRef={}. Possible configuration issue with VNPay secret key.", vnp_TxnRef);
            return ResponseEntity.ok(Map.of("Rspcode", "01", "Message", "Invalid secure hash"));
        }

        try {
            Long paymentId = Long.parseLong(vnp_TxnRef);
            Payment payment = paymentService.getPaymentById(paymentId);

            if (payment.getTransactionId() != null && payment.getTransactionId().equals(vnp_TransactionNo)) {
                log.warn("Duplicate transaction for TxnRef={}, TransactionNo={}. Current status: {}", vnp_TxnRef, vnp_TransactionNo, payment.getStatus());
                return ResponseEntity.ok(Map.of("Rspcode", "00", "Message", "success"));
            }

            PaymentStatus status = "00".equals(vnp_ResponseCode) ? PaymentStatus.PAID : PaymentStatus.FAILED;
            log.info("Updating payment status for TxnRef={} to {}", vnp_TxnRef, status);
            paymentService.updatePaymentStatus(payment.getOrder().getId(), status, vnp_TransactionNo);

            if (status == PaymentStatus.PAID) {
                log.info("Confirming order for payment TxnRef={}", vnp_TxnRef);
                orderService.confirmOrder(payment.getOrder().getId(), null);
            } else {
                log.warn("Payment failed for TxnRef={}. ResponseCode={}", vnp_TxnRef, vnp_ResponseCode);
            }

            return ResponseEntity.ok(Map.of("Rspcode", "00", "Message", "success"));
        } catch (NumberFormatException e) {
            log.error("Invalid transaction reference for TxnRef={}: {}", vnp_TxnRef, e.getMessage());
            return ResponseEntity.ok(Map.of("Rspcode", "01", "Message", "Invalid transaction reference"));
        } catch (RuntimeException e) {
            log.error("Payment not found for TxnRef={}: {}", vnp_TxnRef, e.getMessage());
            return ResponseEntity.ok(Map.of("Rspcode", "01", "Message", "Payment not found"));
        } catch (Exception e) {
            log.error("Error processing VNPay IPN for TxnRef={}: {}", vnp_TxnRef, e.getMessage());
            return ResponseEntity.ok(Map.of("Rspcode", "01", "Message", "Processing error"));
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

        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.AWAITING_DELIVERY) {
            return ResponseEntity.badRequest().body(Map.of("message", "Thanh toán đã hoàn tất hoặc đang chờ giao hàng"));
        }

        String paymentUrl;
        switch (payment.getPaymentMethod()) {
            case VNPAY:
                paymentService.updatePaymentStatus(orderId, PaymentStatus.PROCESSING, null);
                paymentUrl = vnPayService.createPayment(payment.getId(), order.getTotalPrice().doubleValue());
                break;
            case COD:
                paymentUrl = "/order-confirmation";
                break;
            case MOMO:
                return ResponseEntity.badRequest().body(Map.of("message", "Phương thức Momo chưa được hỗ trợ"));
            default:
                return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
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