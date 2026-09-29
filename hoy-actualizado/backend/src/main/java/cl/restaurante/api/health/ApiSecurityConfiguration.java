package cl.restaurante.api.health;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiSecurityConfiguration implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    public ApiSecurityConfiguration(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/status", "/api/auth/setup/**",
                        "/api/auth/login", "/api/auth/session", "/api/auth/logout");
    }
}
