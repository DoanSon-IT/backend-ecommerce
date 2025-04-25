package com.sondv.phone.entity;

public enum OrderStatus {
    PENDING,// Chờ xác nhận
    CONFIRMED, // Đã xác nhận
    SHIPPED,      // Đang giao hàng
    COMPLETED,    // Giao hàng thành công
    CANCELLED     // Đã hủy
}
