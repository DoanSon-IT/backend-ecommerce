package com.sondv.phone.security.oauth2.user;

import java.util.Map;

public interface BaseOAuth2User {
    String getEmail();
    String getFullName();
    String getAvatar();
    Map<String, Object> getAttributes();
}