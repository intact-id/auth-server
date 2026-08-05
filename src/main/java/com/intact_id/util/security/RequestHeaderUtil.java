package com.intact_id.util.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestHeaderUtil {

    public String getHeader(HttpServletRequest request, String headerName) {
        if (isHeaderPresent(request, headerName)) {
            return request.getHeader(headerName);
        }
        return null;
    }

    public boolean isHeaderPresent(HttpServletRequest request, String headerName) {
        return request.getHeader(headerName) != null;
    }

    public String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}