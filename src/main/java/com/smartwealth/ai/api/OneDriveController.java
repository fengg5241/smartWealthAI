package com.smartwealth.ai.api;

import com.smartwealth.ai.service.OneDriveSyncService;
import com.smartwealth.ai.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sync/onedrive")
public class OneDriveController {

    @Autowired(required = false)
    private OneDriveSyncService syncService;

    private static String urlEncodeParam(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private final java.util.concurrent.ConcurrentHashMap<String, String> pendingStates = new java.util.concurrent.ConcurrentHashMap<>();

    private static String resolveTenant(String queryParamTenantId) {
        String fromContext = TenantContext.getCurrentTenantId();
        if (fromContext != null && !fromContext.isBlank()) return fromContext;
        if (queryParamTenantId != null && !queryParamTenantId.isBlank()) return queryParamTenantId;
        return null;
    }

    private boolean available() {
        return syncService != null && syncService.isEnabled();
    }

    private static final String NOT_CONFIGURED_HTML = """
            <html><body style="font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:40px;text-align:center;max-width:560px;margin:0 auto;">
            <h2 style="color:#dc2626;">&#9888; OneDrive Not Configured</h2>
            <p style="color:#64748b;">Set <code>ONEDRIVE_ENABLED=true</code> and configure OneDrive OAuth credentials in your environment, then restart the service.</p>
            <p style="margin-top:20px;"><a href="/admin.html" style="color:#2563eb;">Back to Admin</a></p>
            </body></html>""";

    @GetMapping("/auth")
    public ResponseEntity<String> startAuth(@RequestParam(value = "tenantId", required = false) String tenantIdParam) {
        if (!available()) {
            return ResponseEntity.ok(NOT_CONFIGURED_HTML);
        }

        String tenantId = resolveTenant(tenantIdParam);
        if (tenantId == null) {
            return ResponseEntity.badRequest().body("<html><body><h2>Missing tenant</h2></body></html>");
        }

        String state = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        pendingStates.put(state, tenantId);

        String url = syncService.getAuthUrl(tenantId, state);

        return ResponseEntity.ok("""
                <html><body style="font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:40px;text-align:center;max-width:560px;margin:0 auto;">
                <h2 style="color:#2563eb;">Link OneDrive</h2>
                <div style="text-align:left;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin:20px 0;">
                <p style="font-weight:600;">What SmartRAG will do:</p>
                <ul style="line-height:1.8;font-size:14px;">
                <li>Create a <strong>SmartRAG</strong> folder in your OneDrive</li>
                <li>Only scan files inside that folder</li>
                <li>Only download PDF, DOCX, XLSX, XLS — <strong>all other files ignored</strong></li>
                <li>Skip any file larger than <strong>10MB</strong></li>
                </ul>
                <p style="font-size:13px;color:#64748b;margin-top:12px;">SmartRAG will <strong>never</strong> access files outside the SmartRAG folder, and will never delete or modify anything in your OneDrive.</p>
                </div>
                <a href="%s" style="display:inline-block;padding:12px 32px;background:#2563eb;color:white;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px;">Continue to Microsoft</a>
                <p style="font-size:12px;color:#64748b;margin-top:16px;">You will be redirected to Microsoft to sign in and authorize.</p>
                </body></html>""".formatted(url));
    }

    @GetMapping("/callback")
    public ResponseEntity<String> oauthCallback(@RequestParam(value = "code", required = false) String code,
                                                 @RequestParam(value = "state", required = false) String state,
                                                 @RequestParam(value = "error", required = false) String error) {
        if (!available()) {
            return ResponseEntity.ok(NOT_CONFIGURED_HTML);
        }
        if (error != null) {
            return ResponseEntity.badRequest().body("Authorization denied: " + error);
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("<html><body><h2>Missing authorization code</h2><p>Please start from the Connect button in Admin settings.</p></body></html>");
        }

        String tenantId = null;
        if (state != null && state.contains(":")) {
            tenantId = state.split(":")[0];
        } else if (state != null) {
            tenantId = pendingStates.remove(state);
        }

        if (tenantId == null) {
            return ResponseEntity.badRequest().body("Invalid state");
        }

        String result = syncService.exchangeCode(tenantId, code);
        // Trigger immediate sync to create the folder & import files
        if (result != null && result.startsWith("OneDrive connected")) {
            try { syncService.syncTenant(tenantId); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok("<html><body style=\"font-family:sans-serif;padding:40px;text-align:center;\">"
                + "<h2>" + (result != null ? result : "Authorization failed") + "</h2>"
                + "<p><a href=\"/?tenant=" + urlEncodeParam(tenantId) + "\">Back to SmartRAG</a></p>"
                + "<script>setTimeout(function(){window.close();},3000);</script>"
                + "</body></html>");
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestParam(value = "tenantId", required = false) String tenantIdParam) {
        if (!available()) {
            return ResponseEntity.ok(Map.of("connected", false, "enabled", false));
        }
        String tenantId = resolveTenant(tenantIdParam);
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant"));
        }
        return ResponseEntity.ok(Map.of(
                "connected", syncService.isConnected(tenantId),
                "enabled", syncService.isEnabled()
        ));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(
            @RequestParam(value = "tenantId", required = false) String tenantIdParam) {
        if (!available()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OneDrive is not enabled"));
        }
        String tenantId = resolveTenant(tenantIdParam);
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant"));
        }
        syncService.disconnect(tenantId);
        return ResponseEntity.ok(Map.of("message", "Disconnected"));
    }

    @GetMapping("/sync-now")
    @PostMapping("/sync-now")
    public ResponseEntity<Map<String, Object>> syncNow(
            @RequestParam(value = "tenantId", required = false) String tenantIdParam) {
        if (!available()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OneDrive is not enabled"));
        }
        String tenantId = resolveTenant(tenantIdParam);
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant"));
        }
        int count = syncService.syncTenant(tenantId);
        return ResponseEntity.ok(Map.of("synced", count));
    }
}
