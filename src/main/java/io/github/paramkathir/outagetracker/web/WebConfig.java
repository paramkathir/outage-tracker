package io.github.paramkathir.outagetracker.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AdminKeyInterceptor adminKeyInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor, AdminKeyInterceptor adminKeyInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.adminKeyInterceptor = adminKeyInterceptor;
    }

    /** Clean URL for the operator dashboard. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/ops").setViewName("forward:/ops.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/reports", "/api/subscriptions");
        registry.addInterceptor(adminKeyInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
