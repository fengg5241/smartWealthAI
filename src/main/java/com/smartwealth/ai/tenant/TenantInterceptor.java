package com.smartwealth.ai.tenant;

import com.smartwealth.ai.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final String adminKey;
    private final TenantRepository tenantRepository;

    public TenantInterceptor(@Value("${demo.admin-key:}") String adminKey, TenantRepository tenantRepository) {
        this.adminKey = adminKey;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isBlank()) {
            String tid = tenantId.trim();
            // Tenant management endpoints don't require tenant to exist (admin manages them)
            if (!request.getRequestURI().startsWith("/api/admin/tenants")) {
                if (tenantRepository.findByTenantId(tid).isEmpty()) {
                    response.setStatus(404);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Tenant '" + tid + "' not found\"}");
                    return false;
                }
            }
            TenantContext.setCurrentTenantId(tid);
        }

        String key = request.getHeader("X-Admin-Key");
        boolean isAdmin = adminKey != null && !adminKey.isBlank() && adminKey.equals(key);
        TenantContext.setAdmin(isAdmin);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        TenantContext.clear();
    }
}
