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
                                .map(user -> org.springframework.security.core.userdetails.User
                                                .withUsername(user.getEmail())
                                                .password(user.getPassword())
                                                .authorities(user.getRoles().stream()
                                                                .map(role -> "ROLE_" + role.name())
                                                                .toArray(String[]::new))
                                                .build())
                                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
                        throws Exception {
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
                                .cors(cors -> cors.configure(http))
                                .logout(logout -> logout
                                                .logoutUrl("/api/auth/logout")
                                                .logoutSuccessHandler((req, res, auth) -> {
                                                        CookieUtil.clearCookie(res, "auth_token");
                                                        CookieUtil.clearCookie(res, "refresh_token");
                                                        res.setStatus(200);
                                                }))
                                .authorizeHttpRequests(auth -> auth
                                                // 🔓 PUBLIC: không cần login
                                                .requestMatchers(
                                                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                                                "/api-docs/**",
                                                                "/login/oauth2/**", "/oauth2/**",
                                                                "/api/auth/**", "/email_verified_success.html",
                                                                "/email_verified_fail.html")
                                                .permitAll()

                                                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/discounts/active").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/discounts/spin").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/discounts/*").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/reviews/product/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/chatbot/ask").permitAll()
                                                .requestMatchers("/ws/**").permitAll()

                                                // 🔒 AUTHENTICATED: đăng nhập mới dùng được
                                                .requestMatchers(HttpMethod.POST, "/api/discounts/apply-discount")
                                                .authenticated()

                                                // 🔐 ADMIN-ONLY
                                                .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")

                                                .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/categories/**")
                                                .hasRole("ADMIN")

                                                .requestMatchers("/api/discounts").hasRole("ADMIN")
                                                .requestMatchers("/api/discounts/**").hasRole("ADMIN")
                                                .requestMatchers("/api/suppliers/**").hasRole("ADMIN")
                                                .requestMatchers("/api/reports/**").hasRole("ADMIN")
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                                                // 🔐 STAFF + ADMIN
                                                .requestMatchers("/api/orders/**")
                                                .hasAnyRole("CUSTOMER", "ADMIN", "STAFF")

                                                // 🔐 CUSTOMER
                                                .requestMatchers("/api/payments/**").hasRole("CUSTOMER")

                                                .anyRequest().authenticated())
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
