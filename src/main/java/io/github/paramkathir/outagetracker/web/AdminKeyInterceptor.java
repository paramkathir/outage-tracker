package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.config.TrackerProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Minimal shared-secret auth for operator endpoints. A real deployment would put these behind
 * the company SSO (Spring Security + OAuth2/OIDC); this keeps the demo self-contained.
 */
@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-Admin-Key";

    private final byte[] expected;

    public AdminKeyInterceptor(TrackerProperties properties) {
        String key = properties.admin().apiKey();
        this.expected = key == null || key.isBlank() ? null : key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String provided = request.getHeader(HEADER);
        // Constant-time comparison so the key can't be guessed byte-by-byte from response timing.
        if (expected != null && provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"Missing or invalid X-Admin-Key header."}""");
        return false;
    }
}
