package com.smartwealth.ai.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final String adminKey;

    public TenantInterceptor(@Value("${demo.admin-key:}") String adminKey) {
        this.adminKey = adminKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setCurrentTenantId(tenantId.trim());
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
