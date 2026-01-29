package com.speakfit.backend.global.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    private SecurityUtil(){}

    public static Long getCurrentUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) return null;

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof String s) return Long.valueOf(s);

        return null;
    }
}
