package com.truehire.config;

import com.truehire.model.SiteVisit;
import com.truehire.repository.SiteVisitRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Configuration
public class AnalyticsConfig implements WebMvcConfigurer {

    private final SiteVisitRepository visitRepository;

    public AnalyticsConfig(SiteVisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!shouldTrackRequest(request)) return true;
                Object existing = request.getSession(true).getAttribute("analyticsSessionKey");
                String sessionKey = existing instanceof String value ? value : UUID.randomUUID().toString();
                request.getSession().setAttribute("analyticsSessionKey", sessionKey);
                request.setAttribute("analyticsSessionKey", sessionKey);
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        Object handler, Exception ex) {
                if (!shouldTrack(request, response)) return;
                String sessionKey = (String) request.getAttribute("analyticsSessionKey");
                if (sessionKey == null) return;
                String path = request.getRequestURI();
                visitRepository.save(new SiteVisit(sessionKey,
                        path.length() > 255 ? path.substring(0, 255) : path,
                        LocalDateTime.now(ZoneOffset.UTC)));
            }
        });
    }

    private boolean shouldTrack(HttpServletRequest request, HttpServletResponse response) {
        return shouldTrackRequest(request) && response.getStatus() < 400;
    }

    private boolean shouldTrackRequest(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) return false;
        String path = request.getRequestURI();
        if (path.startsWith("/admin") || path.startsWith("/api") || path.startsWith("/css")
                || path.startsWith("/images") || path.startsWith("/error")) return false;
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) return true;
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return !normalized.contains("bot") && !normalized.contains("crawler")
                && !normalized.contains("spider") && !normalized.contains("monitoring");
    }
}
