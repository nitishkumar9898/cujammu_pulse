package com.college.events.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import com.college.events.security.RateLimitInterceptor;

@Configuration
@Slf4j
@SuppressWarnings("null")
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String EVENTS_DIR = "events";
    private static final String PROFILES_DIR = "profiles";

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Autowired
    private FeatureFlagInterceptor featureFlagInterceptor;

    @Autowired
    private MediaExpiryInterceptor mediaExpiryInterceptor;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;
    @Value("${app.client.dist:../cujammu-pulse/dist}")
    private String clientDist;

    @PostConstruct
    public void init() {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            Files.createDirectories(uploadPath.resolve(EVENTS_DIR));
            Files.createDirectories(uploadPath.resolve(PROFILES_DIR));
            log.info("Upload directories ready at {}", uploadPath);
        } catch (IOException e) {
            log.error("Failed to create upload directory", e);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(featureFlagInterceptor)
                .addPathPatterns("/api/v1/cujammu/**", "/feed/**", "/sports/**", "/alumni/**");
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(mediaExpiryInterceptor)
                .addPathPatterns("/uploads/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
                .addResourceLocations(ensureTrailingSlash(Paths.get(clientDist).toAbsolutePath().normalize().toUri().toString()))
                .setCachePeriod(0);
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uploadRoot = ensureTrailingSlash(uploadPath.toUri().toString());
        String legacyEventRoot = ensureTrailingSlash(uploadPath.resolve(EVENTS_DIR).toUri().toString());

        // Uploaded bytes are delivered exclusively by the authorized MediaDeliveryController.
    }

    private String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }
}
