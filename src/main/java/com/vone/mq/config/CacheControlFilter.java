package com.vone.mq.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Component
public class CacheControlFilter extends OncePerRequestFilter {

    private static final Set<String> CACHEABLE_EXTENSIONS = Set.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".ico", ".woff", ".woff2", ".ttf", ".eot", ".apk");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isCacheableMethod(request.getMethod())) {
            String path = request.getRequestURI().toLowerCase(Locale.ROOT);
            if (hasCacheableExtension(path)) {
                response.setHeader(
                        "Cache-Control",
                        "public, max-age=604800, stale-while-revalidate=86400");
            } else if (path.equals("/") || path.endsWith(".html")) {
                response.setHeader("Cache-Control", "no-cache");
            } else {
                response.setHeader("Cache-Control", "no-store");
            }
        } else {
            response.setHeader("Cache-Control", "no-store");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isCacheableMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private boolean hasCacheableExtension(String path) {
        return CACHEABLE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }
}
