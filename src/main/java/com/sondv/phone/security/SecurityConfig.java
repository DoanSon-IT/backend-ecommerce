package com.sondv.phone.security;

import com.sondv.phone.repository.UserRepository;
import com.sondv.phone.security.oauth2.handler.FacebookOAuth2LoginSuccessHandler;
import com.sondv.phone.security.oauth2.handler.GoogleOAuth2LoginSuccessHandler;
import com.sondv.phone.security.oauth2.service.FacebookOAuth2UserService;
import com.sondv.phone.security.oauth2.service.GoogleOidcUserService;
import com.sondv.phone.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private final GoogleOidcUserService googleOidcUserService;
    private final FacebookOAuth2UserService facebookOAuth2UserService;
    private final GoogleOAuth2LoginSuccessHandler googleOAuth2LoginSuccessHandler;
    private final FacebookOAuth2LoginSuccessHandler facebookOAuth2LoginSuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> userRepository.findByEmail(email)
                .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                        .password(user.getPassword())
                        .authorities(user.getRoles().stream()
                                .map(role -> "ROLE_" + role.name())
                                .toArray(String[]::new))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> {
                            CookieUtil.clearCookie(res, "auth_token");
                            CookieUtil.clearCookie(res, "refresh_token");
                            res.setStatus(200);
                        })
                )
                .authorizeHttpRequests(auth -> auth

                        // 🔓 Swagger, OAuth2, Auth – hoàn toàn công khai
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**",
                                "/login/oauth2/**", "/oauth2/**",
                                "/api/auth/**", "/email_verified_success.html", "/email_verified_fail.html"
                        ).permitAll()

                        // 🔓 PUBLIC APIs: khách hàng có thể xem
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/discounts/active").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/discounts/spin").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/discounts/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/product/**").permitAll()

                        // 🔒 Cần đăng nhập để áp mã
                        .requestMatchers(HttpMethod.POST, "/api/discounts/apply-discount").authenticated()

                        // 🔐 ADMIN-ONLY APIs
                        .requestMatchers(HttpMethod.POST, "/api/discounts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/discounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/discounts/**").hasRole("ADMIN")
                        .requestMatchers("/api/products/**").hasRole("ADMIN")
                        .requestMatchers("/api/categories/**").hasRole("ADMIN")
                        .requestMatchers("/api/suppliers/**").hasRole("ADMIN")
                        .requestMatchers("/api/reports/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 🔐 STAFF + ADMIN APIs
                        .requestMatchers("/api/orders/**").hasAnyRole("CUSTOMER", "ADMIN", "STAFF")

                        // 🔐 CUSTOMER-only APIs
                        .requestMatchers("/api/payments/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/reviews").authenticated()

                        // 🔓 WebSocket
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/ask").permitAll()

                        // ❗ Tất cả còn lại phải đăng nhập
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(userRequest -> {
                                    String provider = userRequest.getClientRegistration().getRegistrationId();
                                    if ("google".equals(provider)) {
                                        return googleOidcUserService.loadUser((OidcUserRequest) userRequest);
                                    } else if ("facebook".equals(provider)) {
                                        return facebookOAuth2UserService.loadUser(userRequest);
                                    }
                                    throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + provider);
                                })
                        )
                        .successHandler((request, response, authentication) -> {
                            String provider = authentication.getAuthorities().toString().contains("google") ? "google" : "facebook";
                            if (provider.equals("google")) {
                                googleOAuth2LoginSuccessHandler.onAuthenticationSuccess(request, response, authentication);
                            } else {
                                facebookOAuth2LoginSuccessHandler.onAuthenticationSuccess(request, response, authentication);
                            }
                        })
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
