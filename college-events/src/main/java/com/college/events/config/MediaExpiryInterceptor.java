package com.college.events.config;

import com.college.events.model.MediaAsset;
import com.college.events.repository.MediaAssetRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/** Fails closed for known expired managed assets before static resource handling. */
@Component
@RequiredArgsConstructor
public class MediaExpiryInterceptor implements HandlerInterceptor {
    private final MediaAssetRepository assets;
    private final com.college.events.service.MediaAccessService access;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String prefix = request.getContextPath() + "/uploads/";
        if (!uri.startsWith(prefix)) return true;
        String objectKey = uri.substring(prefix.length());
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("X-Content-Type-Options", "nosniff");
        MediaAsset asset = assets.findByObjectKey(objectKey).orElse(null);
        if (asset == null) { response.setStatus(HttpServletResponse.SC_NOT_FOUND); return false; }
        if (!access.mayRead(asset)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND); return false;
        }
        Instant now = Instant.now();
        if ("ACTIVE".equals(asset.getStatus()) && !asset.isLegalHold() && asset.getExpiresAt()!=null && asset.getExpiresAt().isAfter(now)) return true;
        response.setStatus(HttpServletResponse.SC_GONE);
        return false;
    }
}
