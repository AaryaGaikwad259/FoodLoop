package com.foodloop.util;

import org.springframework.security.core.Authentication;

public final class SecurityUtil {
    private SecurityUtil() {}

    public static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
