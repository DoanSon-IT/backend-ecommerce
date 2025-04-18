package com.sondv.phone.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sondv.phone.security.oauth2.user.BaseOAuth2User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String fullName;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    @JsonIgnore
    private String password;

    @Column(unique = true, length = 15)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private String verificationToken;

    @Column(name = "verification_token_created_at")
    private LocalDateTime verificationTokenCreatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name")
    private Set<RoleName> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean isVerified = false;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime chatBanUntil;

    private String resetToken;

    @Column(length = 500)
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(length = 500)
    private String avatarUrl;

    @JsonIgnore
    public Set<GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
    }

    public static User oauthUser(BaseOAuth2User userInfo, AuthProvider provider) {
        return User.builder()
                .email(userInfo.getEmail())
                .fullName(userInfo.getFullName())
                .avatarUrl(userInfo.getAvatar())

                .phone(null) // tường minh
                .address(null)

                .provider(provider)
                .isVerified(true)
                .roles(Set.of(RoleName.CUSTOMER))
                .password("") // không dùng password với OAuth

                .createdAt(LocalDateTime.now())

                .chatBanUntil(null)
                .refreshToken(null)
                .resetToken(null)
                .verificationToken(null)
                .build();
    }

    public boolean isOAuthUser() {
        return this.provider == AuthProvider.GOOGLE || this.provider == AuthProvider.FACEBOOK;
    }
}
