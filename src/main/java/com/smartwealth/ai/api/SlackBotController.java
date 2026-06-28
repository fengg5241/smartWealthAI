package com.smartwealth.ai.api;

import com.smartwealth.ai.config.SlackProperties;
import com.smartwealth.ai.service.SlackBotService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/slack")
@ConditionalOnProperty(prefix = "slack", name = "enabled", havingValue = "true")
public class SlackBotController {

    private static final Logger log = LoggerFactory.getLogger(SlackBotController.class);

    private final SlackProperties props;
    private final SlackBotService botService;

    public SlackBotController(SlackProperties props, SlackBotService botService) {
        this.props = props;
        this.botService = botService;
    }

    /**
     * Slack OAuth redirect — user installs the app.
     */
    @GetMapping("/oauth/callback")
    public ResponseEntity<String> oauthCallback(@RequestParam("code") String code) {
        if (!botService.isEnabled()) {
            return ResponseEntity.status(503).body("Slack bot is disabled");
        }
        // Build redirect URI from config or request
        String redirectUri = System.getenv("SLACK_REDIRECT_URI");
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "https://" + System.getenv("APP_HOST") + "/api/slack/oauth/callback";
        }

        String[] oauthResult = botService.exchangeOAuthCode(code, redirectUri);
        if (oauthResult == null) {
            return ResponseEntity.badRequest().body("OAuth failed");
        }

        String teamId = oauthResult[0];
        String teamName = oauthResult[1];

        // The admin maps team_id to tenant later in the admin page
        return ResponseEntity.ok("""
                <html><body style="font-family: sans-serif; padding: 40px; text-align: center;">
                <h2>SmartRAG installed to Slack!</h2>
                <p>Team: <strong>%s</strong> (%s)</p>
                <p>An admin needs to map this workspace to a tenant in the <a href="/">SmartRAG admin panel</a>.</p>
                </body></html>
                """.formatted(teamName != null ? teamName : "Unknown", teamId != null ? teamId : "unknown"));
    }

    /**
     * Slack Events API — app_mention, file_shared, url_verification.
     */
    @PostMapping("/events")
    public ResponseEntity<?> handleEvents(HttpServletRequest request) {
        if (!botService.isEnabled()) {
            return ResponseEntity.status(503).body("Slack bot is disabled");
        }

        try {
            String body = readBody(request);
            String slackSignature = request.getHeader("X-Slack-Signature");
            String slackTimestamp = request.getHeader("X-Slack-Request-Timestamp");

            if (!verifySignature(slackSignature, slackTimestamp, body)) {
                return ResponseEntity.status(401).body("Invalid signature");
            }

            // Parse the JSON manually (avoid heavy parsing)
            String type = extractJsonString(body, "type");

            if ("url_verification".equals(type)) {
                String challenge = extractJsonString(body, "challenge");
                return ResponseEntity.ok(challenge);
            }

            if ("event_callback".equals(type)) {
                String eventType = extractJsonString(body, "type", 1); // nested event.type
                String teamId = extractJsonString(body, "team_id");
                String eventData = extractJsonObject(body, "event");

                // Resolve bot token for this team (from mapping or default)
                String botToken = props.getBotToken();

                if ("app_mention".equals(eventType) && eventData != null) {
                    String channel = extractJsonString(eventData, "channel");
                    String user = extractJsonString(eventData, "user");
                    String text = extractJsonString(eventData, "text");

                    // Handle in background — Slack expects 200 within 3s
                    final String tId = teamId;
                    final String ch = channel;
                    final String usr = user;
                    final String txt = text;
                    final String token = botToken;
                    new Thread(() -> botService.handleAppMention(tId, ch, usr, txt, token)).start();
                }

                if ("file_shared".equals(eventType) && eventData != null) {
                    String fileId = extractJsonString(eventData, "file_id");
                    if (fileId == null) {
                        fileId = eventData.contains("\"files\"") ? extractJsonString(eventData, "id") : null;
                    }
                    String channelId = extractJsonString(eventData, "channel_id");
                    final String fId = fileId;
                    final String tId = teamId;
                    final String chId = channelId;
                    final String token = botToken;
                    if (fId != null) {
                        new Thread(() -> botService.handleFileShared(tId, fId, chId, token)).start();
                    }
                }
            }

            return ResponseEntity.ok(Map.of());

        } catch (Exception e) {
            log.error("Slack events error", e);
            return ResponseEntity.ok(Map.of());
        }
    }

    /**
     * Slack Slash Command — /ask [question].
     */
    @PostMapping("/commands")
    public ResponseEntity<String> handleCommand(HttpServletRequest request) {
        if (!botService.isEnabled()) {
            return ResponseEntity.status(503).body("Slack bot is disabled");
        }

        try {
            String body = readBody(request);
            String slackSignature = request.getHeader("X-Slack-Signature");
            String slackTimestamp = request.getHeader("X-Slack-Request-Timestamp");

            if (!verifySignature(slackSignature, slackTimestamp, body)) {
                return ResponseEntity.status(401).body("Invalid signature");
            }

            // Parse form-encoded body
            Map<String, String> params = parseFormEncoded(body);

            String teamId = params.get("team_id");
            String channelId = params.get("channel_id");
            String userId = params.get("user_id");
            String text = params.get("text");

            String reply = botService.handleSlashCommand(teamId, channelId, userId, text);
            return ResponseEntity.ok(reply);

        } catch (Exception e) {
            log.error("Slack command error", e);
            return ResponseEntity.ok("Internal error, please try again later.");
        }
    }

    // --- Verification ---

    private boolean verifySignature(String slackSignature, String slackTimestamp, String body) {
        if (slackSignature == null || slackTimestamp == null || props.getSigningSecret() == null) {
            return true; // Allow in dev without signing secret
        }
        try {
            String baseString = "v0:" + slackTimestamp + ":" + body;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    props.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            String hash = "v0=" + bytesToHex(sha256Hmac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
            return hash.equals(slackSignature);
        } catch (Exception e) {
            log.error("Slack signature verification error", e);
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // --- Simple JSON extraction (avoid large dependency for just a few fields) ---

    private static String extractJsonString(String json, String key) {
        return extractJsonString(json, key, 0);
    }

    /**
     * Extract a string value from JSON. `skip` > 0 helps find nested occurrences.
     */
    private static String extractJsonString(String json, String key, int skip) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        int count = 0;
        while (m.find()) {
            if (count >= skip) return m.group(1);
            count++;
        }
        return null;
    }

    private static String extractJsonObject(String json, String key) {
        int start = json.indexOf("\"" + key + "\":");
        if (start < 0) return null;
        start = json.indexOf("{", start);
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return json.substring(start, i + 1); }
        }
        return null;
    }

    private static Map<String, String> parseFormEncoded(String body) {
        Map<String, String> map = new java.util.HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static String readBody(HttpServletRequest request) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}
