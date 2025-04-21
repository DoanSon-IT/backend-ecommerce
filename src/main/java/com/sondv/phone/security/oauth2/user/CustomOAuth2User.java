package com.sondv.phone.security.oauth2.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

@RequiredArgsConstructor
public class CustomOAuth2User implements OAuth2User, BaseOAuth2User {

    private final OAuth2User oAuth2User;
    private final String clientName;

    @Override
    public Map<String, Object> getAttributes() {
        return oAuth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oAuth2User.getAuthorities();
    }

    @Override
    public String getName() {
        return oAuth2User.getName();
    }

    @Override
    public String getEmail() {
        Object email = oAuth2User.getAttributes().get("email");
        return email != null ? email.toString() : null;
    }

    @Override
    public String getFullName() {
        Object name = oAuth2User.getAttributes().get("name");
        return name != null ? name.toString() : null;
    }

    @Override
    public String getAvatar() {
        Object picture = oAuth2User.getAttributes().get("picture");

        if (picture instanceof Map<?, ?> pictureMap) {
            Object data = pictureMap.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                String facebookAvatar = dataMap.get("url").toString();

                if (facebookAvatar.contains("default") || facebookAvatar.contains("safe_image.php")) {
                    return null;
                }

                return facebookAvatar;
            }
        }

        return picture != null ? picture.toString() : null;
    }

    public String getOAuth2ClientName() {
        return clientName;
    }
}
