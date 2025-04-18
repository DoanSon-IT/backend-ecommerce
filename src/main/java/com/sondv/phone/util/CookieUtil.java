package com.sondv.phone.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

public class CookieUtil {

    private static boolean isProduction() {
        String env = System.getenv("ENVIRONMENT");
        return "production".equalsIgnoreCase(env);
    }

    // ✅ Hàm chính đầy đủ tham số
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge, boolean httpOnly, String sameSite) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(isProduction());
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);

        StringBuilder cookieHeader = new StringBuilder();
        cookieHeader.append(String.format("%s=%s; Path=/; Max-Age=%d; ", name, value, maxAge));
        if (httpOnly) cookieHeader.append("HttpOnly; ");
        if (isProduction()) cookieHeader.append("Secure; ");
        cookieHeader.append("SameSite=").append(sameSite);

        response.addHeader("Set-Cookie", cookieHeader.toString());
        response.addCookie(cookie);
    }

    // ✅ Dùng khi muốn default: HttpOnly=true, SameSite auto theo môi trường
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge, boolean httpOnly) {
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
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }

    public static void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(isProduction());
        cookie.setPath("/");
        cookie.setMaxAge(0);

        StringBuilder clearHeader = new StringBuilder();
        clearHeader.append(String.format("%s=; Path=/; Max-Age=0; HttpOnly; ", name));
        clearHeader.append(isProduction() ? "Secure; SameSite=None" : "SameSite=Lax");

        response.addHeader("Set-Cookie", clearHeader.toString());
        response.addCookie(cookie);
    }
}
