package com.college.events.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/** Shared web header policy. Legacy inline handlers remain a separately tracked migration. */
public final class BrowserSecurityHeaders implements HeaderWriter {
    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean sharedClient = path.equals("/app") || path.startsWith("/app/");
        String scripts = sharedClient ? "'self'" : "'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com https://code.jquery.com https://cdnjs.cloudflare.com";
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src " + scripts
                + "; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com https://cdnjs.cloudflare.com;"
                + " font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net https://cdnjs.cloudflare.com;"
                + " img-src 'self' data: blob: https:; media-src 'self' data: blob: https:;"
                + " connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none';");
        response.setHeader("Referrer-Policy", "no-referrer");
        // Camera remains available only to same-origin scanner workflows.
        response.setHeader("Permissions-Policy", "camera=(self), microphone=(), geolocation=(), payment=()");
    }
}
