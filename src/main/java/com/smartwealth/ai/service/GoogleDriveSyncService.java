package com.smartwealth.ai.service;

import com.smartwealth.ai.config.GoogleDriveProperties;
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
@ConditionalOnProperty(prefix = "googledrive", name = "enabled", havingValue = "true")
public class GoogleDriveSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveSyncService.class);

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_API_BASE = "https://www.googleapis.com/drive/v3";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final GoogleDriveProperties props;
    private final SyncAuthTokenRepository tokenRepo;
    private final SyncFileStatusRepository syncRepo;
    private final DocumentParserService documentParser;
    private final RagDocumentService ragDocumentService;

    public GoogleDriveSyncService(GoogleDriveProperties props, SyncAuthTokenRepository tokenRepo,
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

    public String getAuthUrl(String tenantId, String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + urlEncode(props.getClientId())
                + "&redirect_uri=" + urlEncode(props.getRedirectUri())
                + "&response_type=code"
                + "&access_type=offline"
                + "&prompt=consent"
                + "&scope=" + urlEncode("https://www.googleapis.com/auth/drive.file")
                + "&state=" + urlEncode(tenantId + ":" + state);
    }

    /**
     * Exchange authorization code for tokens.
     */
    public String exchangeCode(String tenantId, String code) {
        try {
            String body = "grant_type=authorization_code"
                    + "&code=" + urlEncode(code)
                    + "&redirect_uri=" + urlEncode(props.getRedirectUri())
                    + "&client_id=" + urlEncode(props.getClientId())
                    + "&client_secret=" + urlEncode(props.getClientSecret());

            Map<String, Object> resp = postJson(TOKEN_URL, body, "application/x-www-form-urlencoded");
            if (resp.containsKey("error")) {
                String desc = (String) resp.getOrDefault("error_description", resp.get("error"));
                log.error("Google OAuth error: {}", desc);
                return "Authorization failed: " + desc;
            }

            String accessToken = (String) resp.get("access_token");
            String refreshToken = (String) resp.get("refresh_token");
            Integer expiresIn = ((Number) resp.getOrDefault("expires_in", 3600)).intValue();

            SyncAuthToken token = tokenRepo.findByPlatformAndTenantId("google_drive", tenantId)
                    .orElse(new SyncAuthToken("google_drive", tenantId));
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken != null ? refreshToken : token.getRefreshToken());
            token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            token.setUpdatedAt(LocalDateTime.now());
            tokenRepo.save(token);

            log.info("Google Drive connected for tenant={}", tenantId);
            return "Google Drive connected successfully! You can now close this window.";
        } catch (Exception e) {
            log.error("Google OAuth exchange failed", e);
            return "Authorization failed: " + e.getMessage();
        }
    }

    /**
     * Check if a tenant has Google Drive connected.
     */
    public boolean isConnected(String tenantId) {
        return tokenRepo.findByPlatformAndTenantId("google_drive", tenantId).isPresent();
    }

    /**
     * Disconnect Google Drive for a tenant.
     */
    public void disconnect(String tenantId) {
        tokenRepo.findByPlatformAndTenantId("google_drive", tenantId).ifPresent(tokenRepo::delete);
        log.info("Google Drive disconnected for tenant={}", tenantId);
    }

    /**
     * Scheduled sync — runs every N minutes for all connected tenants.
     */
    @Scheduled(fixedDelayString = "#{@googleDriveProperties.syncIntervalMinutes * 60000}",
               initialDelay = 30_000)
    public void scheduledSync() {
        log.info("Google Drive scheduled sync starting...");
        if (!isEnabled()) return;

        List<SyncAuthToken> tokens = tokenRepo.findAll().stream()
                .filter(t -> "google_drive".equals(t.getPlatform()))
                .toList();

        for (SyncAuthToken token : tokens) {
            try {
                syncTenant(token.getTenantId());
            } catch (Exception e) {
                log.error("Google Drive sync failed for tenant={}", token.getTenantId(), e);
            }
        }
        log.info("Google Drive scheduled sync complete — processed {} tenants", tokens.size());
    }

    /**
     * Sync all changed files for a tenant.
     */
    public int syncTenant(String tenantId) {
        String accessToken = getValidAccessToken(tenantId);
        if (accessToken == null) {
            log.warn("No valid access token for tenant={}", tenantId);
            return 0;
        }

        // Find or create the SmartRAG folder
        String folderId = findOrCreateFolder(accessToken, props.getFolderName());
        if (folderId == null) {
            log.warn("Cannot find/create SmartRAG folder for tenant={} — the OAuth token may lack drive.file scope. Try Disconnect then Connect again.", tenantId);
            return 0;
        }

        // List files modified since last sync
        List<FileInfo> changedFiles = listChangedFiles(accessToken, folderId, tenantId);
        log.info("Google Drive sync for tenant={}: {} files in folder, {} need syncing",
                tenantId, listAllFilesInFolder(accessToken, folderId).size(), changedFiles.size());
        int synced = 0;
        for (FileInfo file : changedFiles) {
            try {
                syncFile(tenantId, accessToken, file);
                synced++;
            } catch (Exception e) {
                log.error("Failed to sync file: {}", file.name, e);
                updateSyncStatus(tenantId, file.id, file.name, file.modifiedTime, "error", e.getMessage());
            }
        }

        if (synced > 0) {
            log.info("Google Drive sync: {} files synced for tenant={}", synced, tenantId);
        }

        // Delete files that were removed from the SmartRAG folder
        int deleted = cleanupDeletedFiles(tenantId, folderId, accessToken);
        if (deleted > 0) {
            log.info("Google Drive cleanup: {} removed files deleted for tenant={}", deleted, tenantId);
        }

        return synced;
    }

    private int cleanupDeletedFiles(String tenantId, String folderId, String accessToken) {
        Set<String> currentIds = new HashSet<>();
        try {
            for (FileInfo f : listAllFilesInFolder(accessToken, folderId)) {
                currentIds.add(f.id);
            }
        } catch (Exception e) {
            log.error("Error listing files for cleanup", e);
            return 0;
        }

        int deleted = 0;
        for (SyncFileStatus record : syncRepo.findByPlatformAndTenantId("google_drive", tenantId)) {
            if (!currentIds.contains(record.getFileId())) {
                try { ragDocumentService.deleteDocument(tenantId, record.getFileName()); }
                catch (Exception e) { log.warn("Failed to delete removed file from vector store: {}", record.getFileName()); }
                syncRepo.delete(record);
                deleted++;
                log.info("Removed deleted file: {} for tenant={}", record.getFileName(), tenantId);
            }
        }
        return deleted;
    }

    private void syncFile(String tenantId, String accessToken, FileInfo file) throws Exception {
        byte[] fileBytes = downloadFile(accessToken, file);
        if (fileBytes == null || fileBytes.length == 0) return;

        // Delete old version from vector store if exists
        syncRepo.findByPlatformAndTenantIdAndFileId("google_drive", tenantId, file.id)
                .ifPresent(old -> {
                    try {
                        ragDocumentService.deleteDocument(tenantId, old.getFileName());
                    } catch (Exception e) {
                        log.warn("Failed to delete old version of {}", old.getFileName());
                    }
                });

        String fileType = file.name.contains(".")
                ? file.name.substring(file.name.lastIndexOf('.') + 1).toUpperCase()
                : "PDF";
        String content = documentParser.parse(fileBytes, file.name);
        int chunks = ragDocumentService.indexDocument(tenantId, file.name, fileType, content);

        updateSyncStatus(tenantId, file.id, file.name, file.modifiedTime, "synced", null);
        log.info("Indexed Google Drive file: {} ({} chunks)", file.name, chunks);
    }

    // --- Google Drive API calls ---

    private String getValidAccessToken(String tenantId) {
        return tokenRepo.findByPlatformAndTenantId("google_drive", tenantId)
                .map(token -> {
                    if (token.getExpiresAt() != null && token.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
                        return token.getAccessToken();
                    }
                    return refreshAccessToken(token);
                })
                .orElse(null);
    }

    private String refreshAccessToken(SyncAuthToken token) {
        try {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + urlEncode(token.getRefreshToken())
                    + "&client_id=" + urlEncode(props.getClientId())
                    + "&client_secret=" + urlEncode(props.getClientSecret());

            Map<String, Object> resp = postJson(TOKEN_URL, body, "application/x-www-form-urlencoded");
            if (resp.containsKey("error")) {
                log.error("Failed to refresh Google token: {}", resp.get("error"));
                return null;
            }

            token.setAccessToken((String) resp.get("access_token"));
            Integer expiresIn = ((Number) resp.getOrDefault("expires_in", 3600)).intValue();
            token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            token.setUpdatedAt(LocalDateTime.now());
            tokenRepo.save(token);

            return token.getAccessToken();
        } catch (Exception e) {
            log.error("Failed to refresh Google access token", e);
            return null;
        }
    }

    private String findOrCreateFolder(String accessToken, String folderName) {
        try {
            String query = "mimeType='application/vnd.google-apps.folder' and name='" + folderName
                    + "' and trashed=false";
            String url = DRIVE_API_BASE + "/files?q=" + urlEncode(query)
                    + "&fields=files(id,name)&pageSize=1";
            Map<String, Object> resp = getJson(url, accessToken);
            List<Map<String, Object>> files = (List<Map<String, Object>>) resp.get("files");
            if (files != null && !files.isEmpty()) {
                return (String) files.get(0).get("id");
            }

            // Create folder
            String createBody = "{\"name\":\"" + folderName + "\",\"mimeType\":\"application/vnd.google-apps.folder\"}";
            Map<String, Object> created = postJson(DRIVE_API_BASE + "/files?fields=id",
                    createBody, "application/json", accessToken);
            return (String) created.get("id");
        } catch (Exception e) {
            log.error("Error finding/creating Google Drive folder: {}", e.getMessage());
            return null;
        }
    }

    private List<FileInfo> listChangedFiles(String accessToken, String folderId, String tenantId) {
        return listFilesInFolder(accessToken, folderId, tenantId, true);
    }

    private List<FileInfo> listAllFilesInFolder(String accessToken, String folderId) {
        return listFilesInFolder(accessToken, folderId, null, false);
    }

    private List<FileInfo> listFilesInFolder(String accessToken, String folderId, String tenantId, boolean skipUnchanged) {
        List<FileInfo> results = new ArrayList<>();
        String pageToken = null;

        try {
            String supportedTypes = "mimeType='application/pdf' or mimeType='application/vnd.openxmlformats-officedocument.wordprocessingml.document' "
                    + "or mimeType='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' "
                    + "or mimeType='application/vnd.ms-excel' "
                    + "or mimeType='application/vnd.google-apps.document' "
                    + "or mimeType='application/vnd.google-apps.spreadsheet'";

            do {
                String query = "('" + folderId + "' in parents) and trashed=false and (" + supportedTypes + ")";
                String url = DRIVE_API_BASE + "/files?q=" + urlEncode(query)
                        + "&fields=files(id,name,mimeType,modifiedTime),nextPageToken"
                        + "&pageSize=50";
                if (pageToken != null) url += "&pageToken=" + urlEncode(pageToken);

                Map<String, Object> resp = getJson(url, accessToken);
                List<Map<String, Object>> files = (List<Map<String, Object>>) resp.get("files");
                if (files != null) {
                    for (Map<String, Object> f : files) {
                        String fileId = (String) f.get("id");
                        String name = (String) f.get("name");
                        String mimeType = (String) f.get("mimeType");
                        String modifiedTime = (String) f.get("modifiedTime");

                        if (skipUnchanged && tenantId != null) {
                            // Check if already synced with same modified time
                            Optional<SyncFileStatus> existing = syncRepo.findByPlatformAndTenantIdAndFileId(
                                    "google_drive", tenantId, fileId);
                            if (existing.isPresent()
                                    && "synced".equals(existing.get().getSyncStatus())
                                    && parseDateTime(modifiedTime).equals(existing.get().getLastModified())) {
                                continue; // No change
                            }
                        }

                        results.add(new FileInfo(fileId, name, mimeType, modifiedTime));
                    }
                }
                pageToken = (String) resp.get("nextPageToken");
            } while (pageToken != null);
        } catch (Exception e) {
            log.error("Error listing Google Drive files", e);
        }

        return results;
    }

    private byte[] downloadFile(String accessToken, FileInfo file) throws Exception {
        byte[] fileBytes = doDownloadFile(accessToken, file);
        if (fileBytes != null && fileBytes.length > MAX_FILE_SIZE) {
            log.warn("Downloaded file '{}' is {}MB — skipping (max 10MB)",
                    file.name, fileBytes.length / 1024 / 1024);
            return null;
        }
        return fileBytes;
    }

    private byte[] doDownloadFile(String accessToken, FileInfo file) throws Exception {
        String downloadUrl;

        // Google Docs/Sheets need export, regular files use direct download
        if ("application/vnd.google-apps.document".equals(file.mimeType)) {
            downloadUrl = DRIVE_API_BASE + "/files/" + file.id
                    + "/export?mimeType=application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (!file.name.toLowerCase().endsWith(".docx")) {
                file.name = file.name + ".docx";
            }
        } else if ("application/vnd.google-apps.spreadsheet".equals(file.mimeType)) {
            downloadUrl = DRIVE_API_BASE + "/files/" + file.id
                    + "/export?mimeType=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            if (!file.name.toLowerCase().endsWith(".xlsx")) {
                file.name = file.name + ".xlsx";
            }
        } else {
            downloadUrl = DRIVE_API_BASE + "/files/" + file.id + "?alt=media";
        }

        HttpURLConnection conn = (HttpURLConnection) URI.create(downloadUrl).toURL().openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

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

    private void updateSyncStatus(String tenantId, String fileId, String fileName,
                                   String modifiedTime, String status, String error) {
        SyncFileStatus s = syncRepo.findByPlatformAndTenantIdAndFileId("google_drive", tenantId, fileId)
                .orElse(new SyncFileStatus("google_drive", tenantId, fileId, fileName));
        s.setFileName(fileName);
        s.setLastModified(parseDateTime(modifiedTime));
        s.setSyncStatus(status);
        s.setErrorMessage(error);
        s.setUpdatedAt(LocalDateTime.now());
        syncRepo.save(s);
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

    private Map<String, Object> parseJson(String json) {
        // Simple JSON parser for flat/lightly nested objects
        Map<String, Object> map = new LinkedHashMap<>();
        json = json.trim();
        if (!json.startsWith("{")) return map;

        // Strip outer braces
        int depth = 0;
        int start = 1;
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
            // Simple array of strings/numbers — just store as parsed
            map.put(key, parseArray(val));
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

    private List<Map<String, Object>> parseArray(String json) {
        List<Map<String, Object>> list = new ArrayList<>();
        json = json.trim();
        if (!json.startsWith("[")) return list;
        int depth = 0;
        int start = 1;
        boolean inString = false;
        for (int i = 1; i < json.length() - 1; i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (c == ',' && depth == 0) {
                list.add(parseJson(json.substring(start, i)));
                start = i + 1;
            }
        }
        String last = json.substring(start, json.length() - 1).trim();
        if (!last.isEmpty()) list.add(parseJson(last));
        return list;
    }

    // --- Data classes ---

    private static class FileInfo {
        String id, name, mimeType, modifiedTime;
        FileInfo(String id, String name, String mimeType, String modifiedTime) {
            this.id = id; this.name = name; this.mimeType = mimeType; this.modifiedTime = modifiedTime;
        }
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
