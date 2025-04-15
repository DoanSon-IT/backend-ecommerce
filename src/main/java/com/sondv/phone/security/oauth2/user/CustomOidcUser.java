package com.sondv.phone.security.oauth2.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import java.util.Collection;
import java.util.Map;

public class CustomOidcUser implements OidcUser, BaseOAuth2User {

    private final OidcUser oidcUser;
    private final String clientName;

    public CustomOidcUser(OidcUser oidcUser, String clientName) {
        this.oidcUser = oidcUser;
        this.clientName = clientName;
    }

    @Override
    public String getEmail() {
        return oidcUser.getEmail();
    }

    @Override
    public String getFullName() {
        return oidcUser.getFullName();
    }

    @Override
    public String getAvatar() {
        Object picture = oidcUser.getAttributes().get("picture");
        return picture != null ? picture.toString() : null;
    }

    public String getOAuth2ClientName() {
        return clientName;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oidcUser.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oidcUser.getAuthorities();
    }

    @Override
    public String getName() {
        return oidcUser.getName();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcUser.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return oidcUser.getIdToken();
    }

    @Override
    public Map<String, Object> getClaims() {
        return oidcUser.getClaims();
    }
}