package com.zoomedu.platform.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RevokedAccessTokenFilter extends OncePerRequestFilter {

    private final AuthSessionStore authSessionStore;
    private final SecurityErrorWriter securityErrorWriter;

    RevokedAccessTokenFilter(
            AuthSessionStore authSessionStore,
            SecurityErrorWriter securityErrorWriter) {
        this.authSessionStore = authSessionStore;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            try {
                if (authSessionStore.isAccessTokenRevoked(jwtAuthentication.getToken().getId())) {
                    SecurityContextHolder.clearContext();
                    securityErrorWriter.write(
                            request, response, 401, "ACCESS_TOKEN_REVOKED", "Access token has been revoked");
                    return;
                }
            } catch (DataAccessException exception) {
                SecurityContextHolder.clearContext();
                securityErrorWriter.write(
                        request, response, 503, "SESSION_STORE_UNAVAILABLE", "Session store is unavailable");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
