package com.sondv.phone.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponseDTO {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String avatarUrl;     // ✅ Thêm ảnh đại diện
    private String provider;      // ✅ LOCAL, GOOGLE, FACEBOOK
    private LocalDateTime createdAt;
    private List<String> roles;   // ✅ Đơn giản hóa Enum
    private boolean isVerified;

    public UserResponseDTO() {}
}
