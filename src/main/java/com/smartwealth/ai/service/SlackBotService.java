package com.smartwealth.ai.service;

import com.smartwealth.ai.config.SlackProperties;
import com.smartwealth.ai.domain.ImTenantMapping;
import com.smartwealth.ai.repository.ImTenantMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "slack", name = "enabled", havingValue = "true")
public class SlackBotService {

    private static final Logger log = LoggerFactory.getLogger(SlackBotService.class);

    private final SlackProperties props;
    private final ImTenantMappingRepository mappingRepo;
    private final RagChatService ragChatService;
    private final DocumentParserService documentParser;
    private final RagDocumentService ragDocumentService;

    public SlackBotService(SlackProperties props, ImTenantMappingRepository mappingRepo,
                           RagChatService ragChatService, DocumentParserService documentParser,
                           RagDocumentService ragDocumentService) {
        this.props = props;
        this.mappingRepo = mappingRepo;
        this.ragChatService = ragChatService;
        this.documentParser = documentParser;
        this.ragDocumentService = ragDocumentService;
    }

    public boolean isEnabled() {
        return props.isEnabled() && props.getClientId() != null && !props.getClientId().isBlank();
    }

    /**
     * Exchange OAuth code for access token. Returns team name or null.
     */
    public String[] exchangeOAuthCode(String code, String redirectUri) {
        try {
            String url = "https://slack.com/api/oauth.v2.access"
                    + "?client_id=" + urlEncode(props.getClientId())
                    + "&client_secret=" + urlEncode(props.getClientSecret())
                    + "&code=" + urlEncode(code)
                    + "&redirect_uri=" + urlEncode(redirectUri);
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            String respBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean ok = extractJsonBool(respBody, "ok");
            if (!ok) {
                log.error("Slack OAuth failed: {}", respBody);
                return null;
            }
            String teamId = extractJsonString(respBody, "team", "id");
            String teamName = extractJsonString(respBody, "team", "name");
            String botToken = extractJsonString(respBody, "access_token");
            return new String[]{teamId, teamName, botToken};
        } catch (Exception e) {
            log.error("Slack OAuth exchange failed", e);
            return null;
        }
    }

    /**
     * Handle slash command /ask [question].
     */
    public String handleSlashCommand(String teamId, String channelId, String userId, String text) {
        String tenantId = resolveTenant(teamId);
        if (tenantId == null) {
            return "This Slack workspace hasn't been linked to a tenant yet. Please contact the admin.";
        }
        if (text == null || text.isBlank()) {
            return "Usage: `/ask <your question>` — Ask questions about your indexed documents.";
        }

        RagChatService.ChatResult result = ragChatService.ask(tenantId, userId, text);
        return buildRichReply(result);
    }

    /**
     * Handle @mention event.
     */
    public void handleAppMention(String teamId, String channelId, String userId, String text, String botToken) {
        String tenantId = resolveTenant(teamId);
        String response;
        if (tenantId == null) {
            response = "This Slack workspace hasn't been linked to a tenant yet. Please contact the admin.";
        } else {
            String question = text.replaceAll("<@\\w+>", "").trim();
            if (question.isEmpty()) {
                response = "How can I help? Ask me a question about your documents, or upload a file to index it.";
            } else {
                RagChatService.ChatResult result = ragChatService.ask(tenantId, userId, question);
                response = buildRichReply(result);
            }
        }
        sendMessage(channelId, response, botToken != null ? botToken : props.getBotToken());
    }

    /**
     * Handle file upload event — download, parse, and index.
     */
    public void handleFileShared(String teamId, String fileId, String channelId, String botToken) {
        String tenantId = resolveTenant(teamId);
        if (tenantId == null) {
            log.warn("No tenant mapping for Slack team {}", teamId);
            return;
        }

        String token = botToken != null ? botToken : props.getBotToken();
        try {
            // Get file info via Slack API
            String infoUrl = "https://slack.com/api/files.info?file=" + urlEncode(fileId);
            HttpURLConnection infoConn = (HttpURLConnection) URI.create(infoUrl).toURL().openConnection();
            infoConn.setRequestProperty("Authorization", "Bearer " + token);
            infoConn.setConnectTimeout(10000);
            infoConn.setReadTimeout(10000);
            String infoBody = new String(infoConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            String fileName = extractJsonString(infoBody, "name");
            String urlPrivate = extractJsonString(infoBody, "url_private");
            if (fileName == null || !isSupportedFile(fileName)) {
                log.info("Skipping unsupported Slack file: {}", fileName);
                return;
            }

            // Download file
            HttpURLConnection dlConn = (HttpURLConnection) URI.create(urlPrivate).toURL().openConnection();
            dlConn.setRequestProperty("Authorization", "Bearer " + token);
            dlConn.setConnectTimeout(15000);
            dlConn.setReadTimeout(60000);
            byte[] fileBytes;
            try (InputStream in = dlConn.getInputStream()) {
                fileBytes = in.readAllBytes();
            }

            String fileType = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase();
            String content = documentParser.parse(fileBytes, fileName);
            int chunks = ragDocumentService.indexDocument(tenantId, fileName, fileType, content);

            if (channelId != null) {
                sendMessage(channelId,
                        "Indexed *" + fileName + "* — " + chunks + " chunks. You can now ask questions about it.",
                        token);
            }
        } catch (Exception e) {
            log.error("Failed to handle Slack file: {}", fileId, e);
        }
    }

    /**
     * Send a message to a Slack channel via chat.postMessage API.
     */
    public void sendMessage(String channelId, String text, String botToken) {
        try {
            String url = "https://slack.com/api/chat.postMessage";
            String body = "channel=" + urlEncode(channelId)
                    + "&text=" + urlEncode(text);
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + botToken);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            String respBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!extractJsonBool(respBody, "ok")) {
                log.warn("Failed to send Slack message: {}", respBody);
            }
        } catch (Exception e) {
            log.error("Error sending Slack message", e);
        }
    }

    private String buildRichReply(RagChatService.ChatResult result) {
        StringBuilder sb = new StringBuilder(result.answer());
        if (!result.sources().isEmpty()) {
            sb.append("\n\n---\n*Sources:* ");
            sb.append(String.join(", ", result.sources()));
        }
        return sb.toString();
    }

    private String resolveTenant(String teamId) {
        return mappingRepo.findByPlatformAndPlatformTeamId("slack", teamId)
                .map(ImTenantMapping::getTenantId)
                .orElse(null);
    }

    private static boolean isSupportedFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    // Simple JSON extraction helpers

    private static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String extractJsonString(String json, String parentKey, String childKey) {
        int parentStart = json.indexOf("\"" + parentKey + "\":");
        if (parentStart < 0) return null;
        int objStart = json.indexOf("{", parentStart);
        if (objStart < 0) return null;
        int depth = 0;
        int objEnd = -1;
        for (int i = objStart; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') { depth--; if (depth == 0) { objEnd = i; break; } }
        }
        if (objEnd < 0) return null;
        return extractJsonString(json.substring(objStart, objEnd + 1), childKey);
    }

    private static boolean extractJsonBool(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        return m.find() && "true".equals(m.group(1));
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
