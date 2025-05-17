package com.sondv.phone.controller;

import com.sondv.phone.dto.AuthRequest;
import com.sondv.phone.dto.AuthResponse;
import com.sondv.phone.exception.ApiException;
import com.sondv.phone.entity.User;
import com.sondv.phone.repository.UserRepository;
import com.sondv.phone.security.JwtUtil;
import com.sondv.phone.util.CookieUtil;
import com.sondv.phone.service.AuthService;
import com.sondv.phone.validation.ValidationGroup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Validated(ValidationGroup.Register.class) @RequestBody AuthRequest request) {
        Map<String, String> response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        logger.info("🔑 Login attempt for user: {}", request.getEmail());
        Map<String, String> tokens = authService.login(request);

        logger.info("✅ Login successful, setting cookies");
        CookieUtil.addCookie(response, "auth_token", tokens.get("accessToken"), 15 * 60, true, "None");
        CookieUtil.addCookie(response, "refresh_token", tokens.get("refreshToken"), 7 * 24 * 60 * 60, true, "None");

        return ResponseEntity.ok(new AuthResponse("Đăng nhập thành công"));
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestParam("token") String token) {
        Map<String, String> response = authService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Map<String, String> response = authService.resendVerification(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String message = authService.sendResetPasswordEmail(email);
        return ResponseEntity.ok(new AuthResponse(message));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        String message = authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(new AuthResponse(message));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(value = "refresh_token", defaultValue = "") String refreshToken,
            HttpServletResponse response) {
        logger.info("🔄 Refresh token request received");

        if (refreshToken.isEmpty()) {
            logger.warn("⚠️ No refresh token found in request");
            return ResponseEntity.status(401).body(new AuthResponse("Không tìm thấy refresh token!"));
        }

        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> {
                    logger.error("❌ Invalid refresh token");
                    return new ApiException(401, "Refresh Token không hợp lệ!");
                });

        if (!jwtUtil.validateRefreshToken(refreshToken, user.getEmail())) {
            logger.error("❌ Refresh token validation failed for user: {}", user.getEmail());
            throw new ApiException(401, "Refresh Token không hợp lệ hoặc đã hết hạn!");
        }

        String newAccessToken = jwtUtil.generateToken(user);
        logger.info("✅ Generated new access token for user: {}", user.getEmail());

        CookieUtil.addCookie(response, "auth_token", newAccessToken, 15 * 60, true, "None");
        return ResponseEntity.ok(new AuthResponse("Làm mới Access Token thành công!"));
    }

    @GetMapping("/check-cookie")
    public ResponseEntity<AuthResponse> checkCookie(HttpServletRequest request) {
        logger.info("🔍 Checking auth cookie");
        Optional<String> tokenOpt = CookieUtil.getCookieValue(request, "auth_token");
        if (tokenOpt.isPresent()) {
            logger.info("✅ Auth cookie found");
            return ResponseEntity.ok(new AuthResponse("Cookie tồn tại"));
        } else {
            logger.warn("⚠️ No auth cookie found");
            return ResponseEntity.ok(new AuthResponse("Không tìm thấy cookie auth_token"));
        }
    }
}