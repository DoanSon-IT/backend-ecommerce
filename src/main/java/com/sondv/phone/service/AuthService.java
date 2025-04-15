package com.sondv.phone.service;

import com.sondv.phone.dto.AuthRequest;
import com.sondv.phone.exception.ApiException;
import com.sondv.phone.model.*;
import com.sondv.phone.repository.*;
import com.sondv.phone.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CustomerRepository customerRepository;

    @Transactional
    public Map<String, String> register(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(400, "Email đã tồn tại!");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new ApiException(400, "Số điện thoại đã được sử dụng!");
        }

        Role userRole = roleRepository.findByRoleName(RoleName.CUSTOMER)
                .orElseGet(() -> roleRepository.save(new Role(null, RoleName.CUSTOMER)));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.getRoles().add(RoleName.CUSTOMER);
        user.setVerified(false);
        user.setProvider(AuthProvider.LOCAL);

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenCreatedAt(LocalDateTime.now());

        user = userRepository.save(user);
        userRepository.flush();

        Customer customer = new Customer();
        customer.setUser(user);
        customer.setLoyaltyPoints(0);
        customerRepository.save(customer);

        sendVerificationEmail(user, verificationToken);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Đăng ký thành công! Vui lòng kiểm tra email để xác thực tài khoản.");
        response.put("verificationToken", verificationToken);
        return response;
    }

    @Transactional
    public Map<String, String> login(AuthRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()
                    )
            );
        } catch (Exception e) {
            throw new ApiException(401, "Email hoặc mật khẩu không đúng!");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản!"));

        if (!user.isVerified()) {
            throw new ApiException(403, "Tài khoản chưa được xác thực. Vui lòng kiểm tra email để xác thực.");
        }

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        return tokens;
    }

    @Transactional
    public Map<String, String> verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ApiException(400, "Mã xác thực không hợp lệ."));

        if (user.isVerified()) {
            return Map.of("message", "Tài khoản đã được xác thực trước đó.");
        }

        if (user.getVerificationTokenCreatedAt() == null ||
                Duration.between(user.getVerificationTokenCreatedAt(), LocalDateTime.now()).toMinutes() > 30) {
            throw new ApiException(400, "Mã xác thực đã hết hạn. Vui lòng yêu cầu gửi lại email xác thực.");
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenCreatedAt(null);
        userRepository.save(user);

        return Map.of("message", "Tài khoản đã được xác minh thành công!");
    }

    @Transactional
    public String sendResetPasswordEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản!"));

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        userRepository.save(user);

        String resetLink = "http://localhost:3000/auth/forgot-password?token=" + resetToken;

        Map<String, String> placeholders = Map.of(
                "fullName", user.getFullName(),
                "resetLink", resetLink
        );

        String html = emailService.loadEmailTemplate("reset_password_email.html", placeholders);
        emailService.sendEmail(user.getEmail(), "🔐 Khôi phục mật khẩu - Doan Son Store", html);

        return "Email đặt lại mật khẩu đã được gửi!";
    }

    @Transactional
    public String resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new ApiException(400, "Token đặt lại mật khẩu không hợp lệ!"));

        if (newPassword.length() < 6 || !newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).{6,}$")) {
            throw new ApiException(400, "Mật khẩu mới phải có ít nhất 6 ký tự, gồm chữ và số!");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        userRepository.save(user);

        Map<String, String> placeholders = Map.of(
                "fullName", user.getFullName()
        );

        String html = emailService.loadEmailTemplate("reset_success_notify.html", placeholders);
        emailService.sendEmail(user.getEmail(), "🔐 Mật khẩu đã được thay đổi - Doan Son Store", html);


        return "✅ Mật khẩu của bạn đã được đặt lại thành công!";
    }

    @Transactional
    public String logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản!"));
        user.setRefreshToken(null);
        userRepository.save(user);
        SecurityContextHolder.clearContext();
        return "Đăng xuất thành công!";
    }

    private void sendVerificationEmail(User user, String token) {
        String subject = "Xác thực tài khoản của bạn - Doan Son Store";
        String verifyLink = "http://localhost:3000/auth/verify?token=" + token;

        Map<String, String> placeholders = Map.of(
                "fullName", user.getFullName(),
                "verifyLink", verifyLink
        );

        String html = emailService.loadEmailTemplate("verification_email.html", placeholders);
        emailService.sendEmail(user.getEmail(), subject, html);
    }

    @Transactional
    public Map<String, String> resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(404, "Không tìm thấy tài khoản với email này!"));

        if (user.isVerified()) {
            throw new ApiException(400, "Tài khoản đã được xác thực!");
        }

        String newToken = UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        userRepository.save(user);
        userRepository.flush();

        sendVerificationEmail(user, newToken);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Email xác thực đã được gửi lại!");
        return response;
    }
}
