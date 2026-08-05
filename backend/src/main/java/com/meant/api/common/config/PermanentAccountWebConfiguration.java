package com.meant.api.common.config;

import com.meant.api.common.security.PermanentAccountInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class PermanentAccountWebConfiguration implements WebMvcConfigurer {

    private final PermanentAccountInterceptor permanentAccountInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permanentAccountInterceptor).addPathPatterns("/api/**");
    }
}
