package com.sondv.phone.controller;

import com.sondv.phone.dto.*;
import com.sondv.phone.model.*;
import com.sondv.phone.repository.*;
import com.sondv.phone.service.InventoryService;
import com.sondv.phone.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    // Khai báo Logger
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest orderRequest, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        Order order = orderService.createOrder(user, orderRequest);
        return ResponseEntity.ok(order);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ResponseEntity<?> getOrders(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Collections.singletonMap("message", "Chưa đăng nhập!"));
        }

        User user = (User) authentication.getPrincipal();
        Optional<Customer> customer = customerRepository.findByUserId(user.getId());

        List<Order> orders;
        if (customer.isPresent()) {
            orders = orderRepository.findByCustomerId(customer.get().getId());
        } else if (user.getRoles().stream().anyMatch(role -> role == RoleName.ADMIN || role == RoleName.STAFF)) {
            orders = orderRepository.findAll();
        } else {
            return ResponseEntity.status(403).body(Collections.singletonMap("message", "Không có quyền truy cập!"));
        }

        List<OrderResponse> response = orders.stream()
                .map(orderService::mapToOrderResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ResponseEntity<?> getOrderById(@PathVariable Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Collections.singletonMap("message", "Chưa đăng nhập!"));
        }

        User user = (User) authentication.getPrincipal();

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));

        boolean isOwner = order.getCustomer().getUser().getId().equals(user.getId());
        boolean isAdminOrStaff = user.getRoles().stream()
                .anyMatch(role -> role == RoleName.ADMIN || role == RoleName.STAFF);

        if (!isOwner && !isAdminOrStaff) {
            return ResponseEntity.status(403).body(Collections.singletonMap("message", "Bạn không có quyền truy cập đơn hàng này."));
        }

        OrderResponse response = orderService.mapToOrderResponse(order);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ResponseEntity<?> getPaginatedOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Collections.singletonMap("message", "Chưa đăng nhập!"));
        }

        User user = (User) authentication.getPrincipal();

        // ✅ Gọi service xử lý phân trang
        Page<OrderResponse> result = orderService.getPaginatedOrders(
                user, page, size, sort, direction, status, customerName, orderId, startDate, endDate
        );

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long id,
                                                   @RequestBody String newStatus,
                                                   Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(null);
        }

        User user = (User) authentication.getPrincipal();

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));

        OrderStatus status = OrderStatus.valueOf(newStatus.toUpperCase());

        if (status == OrderStatus.COMPLETED) {
            for (OrderDetail detail : order.getOrderDetails()) {
                inventoryService.adjustInventory(
                        detail.getProduct().getId(),
                        -detail.getQuantity(),
                        "Hoàn thành đơn hàng",
                        user.getId()
                );
            }
        }

        order.setStatus(status);
        return ResponseEntity.ok(orderRepository.save(order));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            Order canceledOrder = orderService.cancelOrder(id, user);
            OrderResponse response = orderService.mapToOrderResponse(canceledOrder);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));
        orderRepository.delete(order);
        return ResponseEntity.noContent().build();
    }
}