package com.sondv.phone.controller;

import com.sondv.phone.dto.StatsResponse;
import com.sondv.phone.dto.TopProductDTO;
import com.sondv.phone.entity.Order;
import com.sondv.phone.entity.Product;
import com.sondv.phone.entity.User;
import com.sondv.phone.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getDashboardStats(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(adminService.getDashboardStats(days));
    }

    @GetMapping("/profit")
    public ResponseEntity<BigDecimal> getProfit(@RequestParam int days) {
        LocalDateTime fromDate = LocalDate.now().minusDays(days).atStartOfDay();
        return ResponseEntity.ok(adminService.getTotalProfit(fromDate));
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<List<Order>> getRecentOrders(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(adminService.getRecentOrders(limit));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<Product>> getTopSellingProducts(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(adminService.getTopSellingProducts(limit));
    }

    @GetMapping("/recent-users")
    public ResponseEntity<List<User>> getRecentUsers(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(adminService.getRecentUsers(limit));
    }

    @GetMapping("/top-products-dto")
    public ResponseEntity<List<TopProductDTO>> getTopSellingProductsDTO(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(adminService.getTopSellingProductsDTO(startDate, endDate, limit));
    }

    @GetMapping("/orders-by-status")
    public ResponseEntity<Map<String, Long>> getOrderCountByStatus() {
        return ResponseEntity.ok(adminService.getOrderCountByStatus());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Map<String, Object>>> getLowStockProducts(@RequestParam(defaultValue = "5") int threshold) {
        return ResponseEntity.ok(adminService.getLowStockProducts(threshold));
    }

    @GetMapping("/users-by-region")
    public ResponseEntity<Map<String, Long>> getUsersByRegion() {
        return ResponseEntity.ok(adminService.getUserCountByRegion());
    }
}
