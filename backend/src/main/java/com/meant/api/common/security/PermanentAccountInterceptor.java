package com.meant.api.common.security;

import static com.meant.api.module.user.service.PermanentAccountPolicy.requirePermanentAccount;

import com.meant.api.module.user.service.dto.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermanentAccountInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !requiresPermanentAccount(method)) {
            return true;
        }
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            requirePermanentAccount(AuthenticatedUser.fromJwt(token.getToken()));
        }
        return true;
    }

    private boolean requiresPermanentAccount(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), PermanentAccountRequired.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PermanentAccountRequired.class);
    }
}
