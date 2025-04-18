package com.sondv.phone.controller;

import com.sondv.phone.dto.ShippingEstimateDTO;
import com.sondv.phone.dto.ShippingEstimateRequest;
import com.sondv.phone.dto.ShippingRequest;
import com.sondv.phone.entity.Order;
import com.sondv.phone.entity.ShippingInfo;
import com.sondv.phone.repository.OrderRepository;
import com.sondv.phone.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;
    private final OrderRepository orderRepository;

    @PostMapping("/estimate")
    public ResponseEntity<ShippingEstimateDTO> estimateShipping(@RequestBody ShippingEstimateRequest request) {
        ShippingEstimateDTO result = shippingService.estimateShipping(request.getAddress(), request.getCarrier());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Optional<ShippingInfo>> getShipping(
            @PathVariable Long orderId,
            Authentication authentication) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Đơn hàng không tồn tại!"));

        String email = authentication.getName();
        boolean isCustomer = order.getCustomer().getUser().getEmail().equals(email);
        boolean isAdminOrStaff = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") || auth.getAuthority().equals("ROLE_STAFF"));

        if (!isCustomer && !isAdminOrStaff) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(shippingService.getShippingByOrderId(orderId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ShippingInfo> createShipping(@RequestBody ShippingRequest shippingRequest) {
        ShippingInfo shippingInfo = shippingService.createShipping(
                shippingRequest.getOrderId(),
                shippingRequest.getCarrier(),
                "",
                ""
        );
        return ResponseEntity.ok(shippingInfo);
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ShippingInfo> updateShipping(
            @PathVariable Long orderId,
            @RequestBody ShippingRequest shippingRequest) {
        return ResponseEntity.ok(shippingService.updateShippingInfo(
                orderId,
                shippingRequest.getCarrier(),
                shippingRequest.getTrackingNumber(),
                shippingRequest.getEstimatedDelivery()
        ));
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<String> deleteShipping(@PathVariable Long orderId) {
        shippingService.deleteShipping(orderId);
        return ResponseEntity.ok("Xóa thông tin vận chuyển thành công!");
    }
}