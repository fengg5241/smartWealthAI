package com.smartwealth.ai.tenant;

import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;

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
        // Fallback to query param for SSE EventSource (which can't set headers)
        if ((tenantId == null || tenantId.isBlank()) && "GET".equalsIgnoreCase(request.getMethod())) {
            tenantId = request.getParameter("tid");
        }
        if (tenantId != null && !tenantId.isBlank()) {
            String tid = tenantId.trim();
            if (!request.getRequestURI().startsWith("/api/admin/tenants")) {
                Tenant tenant = tenantRepository.findByTenantId(tid).orElse(null);
                if (tenant == null) {
                    response.setStatus(404);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Tenant '" + tid + "' not found\"}");
                    return false;
                }

                // Subscription enforcement for study tenants
                if ("study".equals(tenant.getTenantGroup())) {
                    boolean hasActiveSub = tenant.getSubscriptionExpiry() != null
                            && !tenant.getSubscriptionExpiry().isBefore(LocalDate.now());
                    boolean hasTrial = tenant.getTrialEndsAt() != null
                            && !tenant.getTrialEndsAt().isBefore(LocalDate.now());

                    if (!hasActiveSub && !hasTrial) {
                        // Allow checkout requests so expired users can still pay
                        String uri = request.getRequestURI();
                        if (uri.startsWith("/api/stripe/")) {
                            // pass through — payment flow needs X-Tenant-ID context
                        } else {
                            String method = request.getMethod();
                            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                                response.setStatus(402);
                                response.setContentType("application/json");
                                response.getWriter().write(
                                        "{\"error\":\"Subscription expired\",\"subscriptionExpired\":true}");
                                return false;
                            }
                        }
                    }
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
