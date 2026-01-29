package com.speakfit.backend.global.util;

import com.speakfit.backend.global.config.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    private SecurityUtil(){}

    public static AuthPrincipal getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal ap) return ap;

        return null;
    }

    public static Long getCurrentUserId() {
        AuthPrincipal ap = getPrincipal();
        return ap == null ? null : ap.getUserId();
    }

    public static String getCurrentUsersId() {
        AuthPrincipal ap = getPrincipal();
        return ap == null ? null : ap.getUsersId();
    }
}
