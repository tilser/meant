package com.meant.api.common.config;

import com.meant.api.common.properties.RateLimitProperties;
import com.meant.api.common.service.RateLimitService;
import com.meant.api.common.service.RateLimitService.RateLimitDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Slf4j
public class ExpensiveEndpointRateLimitFilter extends OncePerRequestFilter {

    private static final String RETRY_AFTER_SECONDS_HEADER = "X-Rate-Limit-Retry-After-Seconds";
    private static final String ANONYMOUS_USER = "anonymousUser";

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final List<RateLimitedEndpoint> endpoints;

    public ExpensiveEndpointRateLimitFilter(RateLimitProperties properties, RateLimitService rateLimitService) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.endpoints = properties.expensiveEndpoints().endpoints().stream()
                .map(endpoint -> new RateLimitedEndpoint(
                        endpoint.method().toUpperCase(),
                        PathPatternParser.defaultInstance.parse(endpoint.path())
                ))
                .toList();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = requestPath(request);
        if (!properties.enabled() || !matchesEndpoint(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitKey key = rateLimitKey(request);
        RateLimitDecision decision = rateLimitService.consume("expensive-endpoints:" + key.type() + ":" + key.value());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = retryAfterSeconds(decision.retryAfter());
        log.warn(
                "Expensive endpoint rate limit exceeded. keyType={}, key={}, method={}, path={}, retryAfterSeconds={}",
                key.type(),
                key.value(),
                request.getMethod(),
                path,
                retryAfterSeconds
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setHeader(RETRY_AFTER_SECONDS_HEADER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":"rate_limit_exceeded","message":"Too many expensive requests. Try again later."}
                """);
    }

    private boolean matchesEndpoint(String method, String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        return endpoints.stream()
                .anyMatch(endpoint -> endpoint.matches(method, pathContainer));
    }

    private RateLimitKey rateLimitKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && StringUtils.hasText(authentication.getName())
                && !ANONYMOUS_USER.equals(authentication.getName())) {
            return new RateLimitKey("user", authentication.getName());
        }
        return new RateLimitKey("ip", clientIp(request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private long retryAfterSeconds(Duration retryAfter) {
        long seconds = retryAfter.toSeconds();
        if (!retryAfter.minusSeconds(seconds).isZero()) {
            seconds++;
        }
        return Math.max(1L, seconds);
    }

    private record RateLimitKey(String type, String value) {
    }

    private record RateLimitedEndpoint(String method, PathPattern pathPattern) {

        private boolean matches(String requestMethod, PathContainer requestPath) {
            return method.equalsIgnoreCase(requestMethod) && pathPattern.matches(requestPath);
        }
    }
}
