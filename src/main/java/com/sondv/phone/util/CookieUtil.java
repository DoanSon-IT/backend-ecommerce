package com.sondv.phone.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

public class CookieUtil {
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge, boolean httpOnly, String sameSite) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setSecure(false); // False cho local (http), true cho production (https)
        // Thêm SameSite vào header Set-Cookie
        response.addHeader("Set-Cookie", String.format("%s=%s; Path=/; Max-Age=%d; %sSameSite=%s",
                name, value, maxAge, httpOnly ? "HttpOnly; " : "", sameSite));
        response.addCookie(cookie);
    }

    // Giữ phương thức cũ với 4 tham số làm mặc định
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        addCookie(response, name, value, maxAge, true, "Lax"); // Mặc định HttpOnly=true, SameSite=Lax
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
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        // Xóa cookie với SameSite=None
        response.addHeader("Set-Cookie", String.format("%s=; Path=/; Max-Age=0; HttpOnly; SameSite=None", name));
        response.addCookie(cookie);
    }
}