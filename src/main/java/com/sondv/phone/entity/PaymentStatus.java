package com.sondv.phone.entity;

public enum PaymentStatus {
    PENDING,          // Chờ thanh toán
    PROCESSING,       // Đang xử lý (cho VNPay, Momo, v.v.)
    PAID,             // Đã thanh toán (VNPay thành công hoặc COD khi giao hàng thành công)
    AWAITING_DELIVERY,// Chờ giao hàng (cho COD sau khi tạo)
    FAILED,           // Thanh toán thất bại
    CANCELLED         // Hủy (khi đơn hàng hủy)
}