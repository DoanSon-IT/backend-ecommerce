package com.sondv.phone.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CookieUtil {
    private static final Logger logger = LoggerFactory.getLogger(CookieUtil.class);

    private static boolean isProduction() {
        String env = System.getenv("ENVIRONMENT");
        return "production".equalsIgnoreCase(env);
    }

    // ✅ Hàm chính đầy đ ủ tham số
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge, boolean httpOnly,
            String sameSite) {
        logger.info("🔧 Adding cookie: name={}, maxAge={}, httpOnly={}, sameSite={}", name, maxAge, httpOnly, sameSite);

        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);

        // Set domain for both production and development
        if (isProduction()) {
            cookie.setDomain(".dsonmobile.shop");
            logger.info("🌐 Setting domain for production: .dsonmobile.shop");
        } else {
            cookie.setDomain("localhost");
            logger.info("🌐 Setting domain for development: localhost");
        }

        StringBuilder cookieHeader = new StringBuilder();
        cookieHeader.append(String.format("%s=%s; Path=/; Max-Age=%d; ", name, value, maxAge));
        if (httpOnly)
            cookieHeader.append("HttpOnly; ");
        cookieHeader.append("Secure; ");
        if (isProduction()) {
            cookieHeader.append("Domain=.dsonmobile.shop; ");
        } else {
            cookieHeader.append("Domain=localhost; ");
        }
        cookieHeader.append("SameSite=").append(sameSite);

        String cookieString = cookieHeader.toString();
        logger.info("🍪 Setting cookie header: {}", cookieString);

        response.addHeader("Set-Cookie", cookieString);
        response.addCookie(cookie);
    }

    // ✅ Dùng khi muốn default: HttpOnly=true, SameSite auto theo môi trường
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge,
            boolean httpOnly) {
        String sameSite = isProduction() ? "None" : "Lax";
        addCookie(response, name, value, maxAge, httpOnly, sameSite);
    }

    // ✅ Mặc định gọn nhất
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        addCookie(response, name, value, maxAge, true);
    }

    public static Optional<String> getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    logger.info("🔍 Found cookie: name={}, value={}", name, cookie.getValue());
                    return Optional.of(cookie.getValue());
                }
            }
        }
        logger.warn("⚠️ Cookie not found: {}", name);
        return Optional.empty();
    }

    public static void clearCookie(HttpServletResponse response, String name) {
        logger.info("🧹 Clearing cookie: {}", name);

        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        if (isProduction()) {
            cookie.setDomain(".dsonmobile.shop");
        } else {
            cookie.setDomain("localhost");
        }

        StringBuilder clearHeader = new StringBuilder();
        clearHeader.append(String.format("%s=; Path=/; Max-Age=0; HttpOnly; ", name));
        clearHeader.append("S        ecure; ");
        if (isProduction()) {
            clearHeader.append("Domain=.dsonmobile.shop; ");
        } else {
            clearHeader.append("Domain=localhost; ");
        }
        clearHeader.append("SameSite=None");

        String cookieString = clearHeader.toString();
        logger.info("🍪 Setting clear cookie header: {}", cookieString);

        response.addHeader("Set-Cookie", cookieString);
        response.addCookie(cookie);
    }
}
