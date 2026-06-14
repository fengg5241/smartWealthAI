package com.smartwealth.ai.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IS_ADMIN = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setAdmin(boolean admin) {
        IS_ADMIN.set(admin);
    }

    public static boolean isAdmin() {
        return Boolean.TRUE.equals(IS_ADMIN.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        IS_ADMIN.remove();
    }
}
