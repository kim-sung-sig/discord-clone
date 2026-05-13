package com.example.discord.guild;

import com.example.discord.auth.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
final class GuildMutationAuthenticationInterceptor implements HandlerInterceptor {
    private final AuthenticatedUserResolver authenticatedUserResolver;

    GuildMutationAuthenticationInterceptor(AuthenticatedUserResolver authenticatedUserResolver) {
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isGuildMutation(request)) {
            authenticatedUserResolver.requireUserId(request.getHeader(HttpHeaders.AUTHORIZATION));
        }
        return true;
    }

    private static boolean isGuildMutation(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return path.startsWith("/api/guilds")
            && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method));
    }
}
