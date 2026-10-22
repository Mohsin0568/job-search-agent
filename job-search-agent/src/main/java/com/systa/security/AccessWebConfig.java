package com.systa.security;

import com.systa.service.AccessService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnWebApplication
public class AccessWebConfig implements WebMvcConfigurer {

    private final AccessService accessService;

    public AccessWebConfig(final AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(new AccessInterceptor(accessService))
                .addPathPatterns("/api/**")
                // Says where the user stands, so it has to answer the users it would turn away.
                .excludePathPatterns("/api/access");
    }
}
