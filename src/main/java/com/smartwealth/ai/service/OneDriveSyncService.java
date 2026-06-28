package com.smartwealth.ai.service;

import com.smartwealth.ai.config.OneDriveProperties;
import com.smartwealth.ai.domain.SyncAuthToken;
import com.smartwealth.ai.domain.SyncFileStatus;
import com.smartwealth.ai.repository.SyncAuthTokenRepository;
import com.smartwealth.ai.repository.SyncFileStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@ConditionalOnProperty(prefix = "onedrive", name = "enabled", havingValue = "true")
public class OneDriveSyncService {

    private static final Logger log = LoggerFactory.getLogger(OneDriveSyncService.class);

    private static final String TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String AUTH_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final OneDriveProperties props;
    private final SyncAuthTokenRepository tokenRepo;
    private final SyncFileStatusRepository syncRepo;
    private final DocumentParserService documentParser;
    private final RagDocumentService ragDocumentService;

    public OneDriveSyncService(OneDriveProperties props, SyncAuthTokenRepository tokenRepo,
                                SyncFileStatusRepository syncRepo, DocumentParserService documentParser,
                                RagDocumentService ragDocumentService) {
        this.props = props;
        this.tokenRepo = tokenRepo;
        this.syncRepo = syncRepo;
        this.documentParser = documentParser;
        this.ragDocumentService = ragDocumentService;
    }

    public boolean isEnabled() {
        return props.isEnabled() && props.getClientId() != null && !props.getClientId().isBlank();
    }

    public String getAuthUrl(String smartragTenantId, String state) {
        String authUrl = String.format(AUTH_URL, props.getTenantId());
        return authUrl
                + "?client_id=" + urlEncode(props.getClientId())
                + "&response_type=code"
                + "&redirect_uri=" + urlEncode(props.getRedirectUri())
                + "&scope=" + urlEncode("Files.Read offline_access")
                + "&state=" + urlEncode(smartragTenantId + ":" + state);
    }

    /**
     * Exchange authorization code for tokens.
     */
    public String exchangeCode(String smartragTenantId, String code) {
        try {
            String tokenUrl = String.format(TOKEN_URL, props.getTenantId());
            String body = "grant_type=authorization_code"
                    + "&code=" + urlEncode(code)
                    + "&redirect_uri=" + urlEncode(props.getRedirectUri())
                    + "&client_id=" + urlEncode(props.getClientId())
                    + "&client_secret=" + urlEncode(props.getClientSecret());

            Map<String, Object> resp = postJson(tokenUrl, body, "application/x-www-form-urlencoded");
            if (resp.containsKey("error")) {
                log.error("OneDrive OAuth error: {}", resp.get("error"));
                return null;
            }

            String accessToken = (String) resp.get("access_token");
            String refreshToken = (String) resp.get("refresh_token");
            Integer expiresIn = ((Number) resp.getOrDefault("expires_in", 3600)).intValue();

            SyncAuthToken token = tokenRepo.findByPlatformAndTenantId("onedrive", smartragTenantId)
                    .orElse(new SyncAuthToken("onedrive", smartragTenantId));
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken != null ? refreshToken : token.getRefreshToken());
            token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            token.setUpdatedAt(LocalDateTime.now());
            tokenRepo.save(token);

            log.info("OneDrive connected for tenant={}", smartragTenantId);
            return "OneDrive connected successfully! You can now close this window.";
        } catch (Exception e) {
            log.error("OneDrive OAuth exchange failed", e);
            return "Authorization failed: " + e.getMessage();
        }
    }

    public boolean isConnected(String smartragTenantId) {
        return tokenRepo.findByPlatformAndTenantId("onedrive", smartragTenantId).isPresent();
    }

    public void disconnect(String smartragTenantId) {
        tokenRepo.findByPlatformAndTenantId("onedrive", smartragTenantId).ifPresent(tokenRepo::delete);
        log.info("OneDrive disconnected for tenant={}", smartragTenantId);
    }

    /**
     * Scheduled sync — runs every 10 minutes for all connected tenants.
     */
    @Scheduled(fixedDelayString = "#{@oneDriveProperties.syncIntervalMinutes * 60000}",
               initialDelay = 60_000)
    public void scheduledSync() {
        log.info("OneDrive scheduled sync starting...");
        if (!isEnabled()) return;

        List<SyncAuthToken> tokens = tokenRepo.findAll().stream()
                .filter(t -> "onedrive".equals(t.getPlatform()))
                .toList();

        for (SyncAuthToken token : tokens) {
            try {
                syncTenant(token.getTenantId());
            } catch (Exception e) {
                log.error("OneDrive sync failed for tenant={}", token.getTenantId(), e);
            }
        }
        log.info("OneDrive scheduled sync complete — processed {} tenants", tokens.size());
    }

    public int syncTenant(String smartragTenantId) {
        String accessToken = getValidAccessToken(smartragTenantId);
        if (accessToken == null) {
            log.warn("No valid OneDrive access token for tenant={}", smartragTenantId);
            return 0;
        }

        // Get / create the SmartRAG folder
        String folderId = findOrCreateFolder(accessToken, props.getFolderName());
        if (folderId == null) {
            log.warn("Cannot find/create OneDrive SmartRAG folder for tenant={}", smartragTenantId);
            return 0;
        }

        List<FileInfo> children = listFolderChildren(accessToken, folderId);
        int synced = 0;
        for (FileInfo file : children) {
            try {
                syncFile(smartragTenantId, accessToken, file);
                synced++;
            } catch (Exception e) {
                log.error("Failed to sync OneDrive file: {}", file.name, e);
            }
        }

        if (synced > 0) {
            log.info("OneDrive sync: {} files synced for tenant={}", synced, smartragTenantId);
        }

        // Delete files that were removed from the SmartRAG folder
        int deleted = cleanupDeletedFiles(smartragTenantId, folderId, accessToken);
        if (deleted > 0) {
            log.info("OneDrive cleanup: {} removed files deleted for tenant={}", deleted, smartragTenantId);
        }

        return synced;
    }

    private int cleanupDeletedFiles(String smartragTenantId, String folderId, String accessToken) {
        Set<String> currentIds = new HashSet<>();
        try {
            for (FileInfo f : listFolderChildren(accessToken, folderId)) {
                currentIds.add(f.id);
            }
        } catch (Exception e) {
            log.error("Error listing OneDrive files for cleanup", e);
            return 0;
        }

        int deleted = 0;
        for (SyncFileStatus record : syncRepo.findByPlatformAndTenantId("onedrive", smartragTenantId)) {
            if (!currentIds.contains(record.getFileId())) {
                try { ragDocumentService.deleteDocument(smartragTenantId, record.getFileName()); }
                catch (Exception e) { log.warn("Failed to delete removed OneDrive file from vector store: {}", record.getFileName()); }
                syncRepo.delete(record);
                deleted++;
                log.info("Removed deleted OneDrive file: {} for tenant={}", record.getFileName(), smartragTenantId);
            }
        }
        return deleted;
    }

    private void syncFile(String smartragTenantId, String accessToken, FileInfo file) throws Exception {
        // Check if already synced
        Optional<SyncFileStatus> existing = syncRepo.findByPlatformAndTenantIdAndFileId(
                "onedrive", smartragTenantId, file.id);
        if (existing.isPresent() && "synced".equals(existing.get().getSyncStatus())
                && file.modifiedTime != null
                && parseDateTime(file.modifiedTime).equals(existing.get().getLastModified())) {
            return; // No change
        }

        if (!isSupportedFile(file.name)) {
            log.debug("Skipping unsupported OneDrive file: {}", file.name);
            return;
        }

        byte[] fileBytes = downloadFile(accessToken, file);
        if (fileBytes == null || fileBytes.length == 0) return;
        if (fileBytes.length > MAX_FILE_SIZE) {
            log.warn("Downloaded file '{}' is {}MB — skipping", file.name, fileBytes.length / 1024 / 1024);
            return;
        }

        // Delete old version
        existing.ifPresent(old -> {
            try { ragDocumentService.deleteDocument(smartragTenantId, old.getFileName()); }
            catch (Exception e) { log.warn("Failed to delete old version of {}", old.getFileName()); }
        });

        String fileType = file.name.contains(".")
                ? file.name.substring(file.name.lastIndexOf('.') + 1).toUpperCase()
                : "UNKNOWN";
        String content = documentParser.parse(fileBytes, file.name);
        int chunks = ragDocumentService.indexDocument(smartragTenantId, file.name, fileType, content);

        SyncFileStatus s = existing.orElse(new SyncFileStatus("onedrive", smartragTenantId, file.id, file.name));
        s.setFileName(file.name);
        s.setLastModified(parseDateTime(file.modifiedTime));
        s.setSyncStatus("synced");
        s.setErrorMessage(null);
        s.setUpdatedAt(LocalDateTime.now());
        syncRepo.save(s);

        log.info("Indexed OneDrive file: {} ({} chunks)", file.name, chunks);
    }

    // --- Microsoft Graph API calls ---

    private String getValidAccessToken(String smartragTenantId) {
        return tokenRepo.findByPlatformAndTenantId("onedrive", smartragTenantId)
                .map(token -> {
                    if (token.getExpiresAt() != null && token.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
                        return token.getAccessToken();
                    }
                    return refreshToken(token);
                })
                .orElse(null);
    }

    private String refreshToken(SyncAuthToken token) {
        try {
            String tokenUrl = String.format(TOKEN_URL, props.getTenantId());
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + urlEncode(token.getRefreshToken())
                    + "&client_id=" + urlEncode(props.getClientId())
                    + "&client_secret=" + urlEncode(props.getClientSecret());

            Map<String, Object> resp = postJson(tokenUrl, body, "application/x-www-form-urlencoded");
            if (resp.containsKey("error")) {
                log.error("Failed to refresh OneDrive token: {}", resp.get("error"));
                return null;
            }

            token.setAccessToken((String) resp.get("access_token"));
            Integer expiresIn = ((Number) resp.getOrDefault("expires_in", 3600)).intValue();
            token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            token.setUpdatedAt(LocalDateTime.now());
            tokenRepo.save(token);

            return token.getAccessToken();
        } catch (Exception e) {
            log.error("Failed to refresh OneDrive token", e);
            return null;
        }
    }

    private String findOrCreateFolder(String accessToken, String folderName) {
        try {
            // Search in root for the folder
            String url = GRAPH_BASE + "/me/drive/root/children"
                    + "?$filter=name eq '" + folderName + "' and folder ne null"
                    + "&$select=id,name";
            Map<String, Object> resp = getJson(url, accessToken);
            List<Map<String, Object>> values = (List<Map<String, Object>>) resp.get("value");
            if (values != null && !values.isEmpty()) {
                return (String) values.get(0).get("id");
            }

            // Create folder
            String createBody = "{\"name\":\"" + folderName + "\",\"folder\":{}}";
            Map<String, Object> created = postJson(GRAPH_BASE + "/me/drive/root/children",
                    createBody, "application/json", accessToken);
            return (String) created.get("id");
        } catch (Exception e) {
            log.error("Error finding/creating OneDrive folder", e);
            return null;
        }
    }

    private List<FileInfo> listFolderChildren(String accessToken, String folderId) {
        List<FileInfo> results = new ArrayList<>();
        try {
            String url = GRAPH_BASE + "/me/drive/items/" + folderId + "/children"
                    + "?$select=id,name,lastModifiedDateTime&$top=100";
            Map<String, Object> resp = getJson(url, accessToken);
            List<Map<String, Object>> values = (List<Map<String, Object>>) resp.get("value");
            if (values != null) {
                for (Map<String, Object> item : values) {
                    String name = (String) item.get("name");
                    String id = (String) item.get("id");
                    String modifiedTime = (String) item.get("lastModifiedDateTime");

                    // Skip folders
                    if (name == null || id == null) continue;

                    // Skip unsupported file types
                    if (!isSupportedFile(name)) {
                        log.debug("Skipping unsupported file: {}", name);
                        continue;
                    }

                    results.add(new FileInfo(id, name, modifiedTime));
                }
            }
        } catch (Exception e) {
            log.error("Error listing OneDrive folder children", e);
        }
        return results;
    }

    private byte[] downloadFile(String accessToken, FileInfo file) throws Exception {
        String url = GRAPH_BASE + "/me/drive/items/" + file.id + "/content";
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        // Follow redirects (Graph API often returns 302 to download URL)
        conn.setInstanceFollowRedirects(false);
        int status = conn.getResponseCode();
        if (status == 302 || status == 301 || status == 307 || status == 308) {
            String redirectUrl = conn.getHeaderField("Location");
            conn = (HttpURLConnection) URI.create(redirectUrl).toURL().openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
        }

        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    // --- HTTP helpers ---

    private Map<String, Object> getJson(String url, String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        return readResponse(conn);
    }

    private Map<String, Object> postJson(String url, String body, String contentType) throws Exception {
        return postJson(url, body, contentType, null);
    }

    private Map<String, Object> postJson(String url, String body, String contentType, String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", contentType);
        if (accessToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private Map<String, Object> readResponse(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        try (InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            byte[] bytes = in != null ? in.readAllBytes() : new byte[0];
            String body = new String(bytes, StandardCharsets.UTF_8);
            if (status >= 400) {
                log.error("HTTP {} calling {}: {}", status, conn.getURL(), body);
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "HTTP " + status);
                err.put("error_description", body);
                return err;
            }
            return parseJson(body);
        }
    }

    // Reuse same JSON parser pattern as GoogleDriveSyncService
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        json = json.trim();
        if (!json.startsWith("{")) return map;

        int depth = 0, start = 1;
        boolean inString = false;
        for (int i = 1; i < json.length() - 1; i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                parsePair(json.substring(start, i), map);
                start = i + 1;
            }
        }
        parsePair(json.substring(start, json.length() - 1), map);
        return map;
    }

    private void parsePair(String pair, Map<String, Object> map) {
        int colon = -1;
        boolean inString = false;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (c == '"' && (i == 0 || pair.charAt(i - 1) != '\\')) inString = !inString;
            if (!inString && c == ':') { colon = i; break; }
        }
        if (colon < 0) return;

        String key = pair.substring(0, colon).trim();
        if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
        String val = pair.substring(colon + 1).trim();

        if (val.startsWith("\"")) {
            map.put(key, val.substring(1, val.length() - 1));
        } else if (val.equals("true") || val.equals("false")) {
            map.put(key, Boolean.parseBoolean(val));
        } else if (val.equals("null")) {
            map.put(key, null);
        } else if (val.startsWith("[")) {
            List<Map<String, Object>> list = new ArrayList<>();
            int d = 0, s = 1;
            boolean is = false;
            for (int i = 1; i < val.length() - 1; i++) {
                char c = val.charAt(i);
                if (c == '"' && (i == 0 || val.charAt(i - 1) != '\\')) is = !is;
                if (is) continue;
                if (c == '{') d++;
                if (c == '}') d--;
                if (c == ',' && d == 0) {
                    list.add(parseJson(val.substring(s, i)));
                    s = i + 1;
                }
            }
            String last = val.substring(s, val.length() - 1).trim();
            if (!last.isEmpty()) list.add(parseJson(last));
            map.put(key, list);
        } else if (val.startsWith("{")) {
            map.put(key, parseJson(val));
        } else {
            try { map.put(key, Long.parseLong(val)); }
            catch (NumberFormatException e1) {
                try { map.put(key, Double.parseDouble(val)); }
                catch (NumberFormatException e2) { map.put(key, val); }
            }
        }
    }

    // --- Data classes ---

    private static class FileInfo {
        String id, name, modifiedTime;
        FileInfo(String id, String name, String modifiedTime) {
            this.id = id; this.name = name; this.modifiedTime = modifiedTime;
        }
    }

    private static boolean isSupportedFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null) return LocalDateTime.now();
        try {
            return LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
