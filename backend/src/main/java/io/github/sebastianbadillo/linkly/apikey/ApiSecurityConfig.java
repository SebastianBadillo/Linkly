package io.github.sebastianbadillo.linkly.apikey;

import io.github.sebastianbadillo.linkly.ratelimit.RateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers ApiKeyFilter only for /api/*. (A filter annotated @Component would run on EVERY URL,
 * including redirects, and would also be pulled into every @WebMvcTest.)
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(ApiKeyService apiKeys, RateLimiter rateLimiter) {
        FilterRegistrationBean<ApiKeyFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyFilter(apiKeys, rateLimiter));
        registration.addUrlPatterns("/api/*"); // servlet pattern: also matches /api/links/abc
        return registration;
    }
}
