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
        Map<String, String> tokens = authService.login(request);

        CookieUtil.addCookie(response, "auth_token", tokens.get("accessToken"), 15 * 60);
        CookieUtil.addCookie(response, "refresh_token", tokens.get("refreshToken"), 7 * 24 * 60 * 60);

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
        if (refreshToken.isEmpty()) {
            return ResponseEntity.status(401).body(new AuthResponse("Không tìm thấy refresh token!"));
        }
        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ApiException(401, "Refresh Token không hợp lệ!"));

        if (!jwtUtil.validateRefreshToken(refreshToken, user.getEmail())) {
            throw new ApiException(401, "Refresh Token không hợp lệ hoặc đã hết hạn!");
        }

        String newAccessToken = jwtUtil.generateToken(user);
        CookieUtil.addCookie(response, "auth_token", newAccessToken, 15 * 60);
        return ResponseEntity.ok(new AuthResponse("Làm mới Access Token thành công!"));
    }

    @GetMapping("/check-cookie")
    public ResponseEntity<AuthResponse> checkCookie(HttpServletRequest request) {
        Optional<String> tokenOpt = CookieUtil.getCookieValue(request, "auth_token");
        if (tokenOpt.isPresent()) {
            return ResponseEntity.ok(new AuthResponse("Cookie tồn tại"));
        } else {
            return ResponseEntity.ok(new AuthResponse("Không tìm thấy cookie auth_token"));
        }
    }
}