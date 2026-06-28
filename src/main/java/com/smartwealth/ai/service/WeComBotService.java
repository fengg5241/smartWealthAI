package com.smartwealth.ai.service;

import com.smartwealth.ai.config.WeComProperties;
import com.smartwealth.ai.repository.ImTenantMappingRepository;
import com.smartwealth.ai.domain.ImTenantMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "wecom", name = "enabled", havingValue = "true")
public class WeComBotService {

    private static final Logger log = LoggerFactory.getLogger(WeComBotService.class);

    private final WeComCryptUtil cryptUtil;
    private final ImTenantMappingRepository mappingRepo;
    private final RagChatService ragChatService;
    private final DocumentParserService documentParser;
    private final RagDocumentService ragDocumentService;
    private final WeComProperties props;

    public WeComBotService(WeComProperties props, ImTenantMappingRepository mappingRepo,
                           RagChatService ragChatService, DocumentParserService documentParser,
                           RagDocumentService ragDocumentService) {
        this.props = props;
        this.cryptUtil = new WeComCryptUtil(props.getToken(), props.getEncodingAesKey(), props.getCorpId());
        this.mappingRepo = mappingRepo;
        this.ragChatService = ragChatService;
        this.documentParser = documentParser;
        this.ragDocumentService = ragDocumentService;
    }

    public boolean isEnabled() {
        return props.isEnabled() && props.getCorpId() != null && !props.getCorpId().isBlank();
    }

    /**
     * Handle GET verification from WeCom server.
     * Returns the decrypted echostr, or null if verification fails.
     */
    public String verifyUrl(String signature, String timestamp, String nonce, String echostr) {
        if (!cryptUtil.verifySignature(signature, timestamp, nonce, echostr)) {
            log.warn("WeCom signature verification failed");
            return null;
        }
        String decrypted = cryptUtil.decrypt(echostr);
        log.info("WeCom URL verified successfully");
        return decrypted;
    }

    /**
     * Handle incoming encrypted message from WeCom.
     */
    public String handleMessage(String encryptedBody, String signature, String timestamp, String nonce) {
        // Decrypt
        String plainXml = cryptUtil.decrypt(encryptedBody);
        if (plainXml == null) {
            return textReply("", "", "", "Failed to process message");
        }

        log.debug("WeCom decrypted message: {}", plainXml);

        try {
            String msgType = extractTag(plainXml, "MsgType");
            String fromUser = extractTag(plainXml, "FromUserName");
            String toUser = extractTag(plainXml, "ToUserName");
            String createTime = extractTag(plainXml, "CreateTime");

            if ("text".equals(msgType)) {
                String content = extractTag(plainXml, "Content");
                return handleTextMessage(fromUser, toUser, createTime, content);
            } else if ("file".equals(msgType)) {
                String mediaId = extractTag(plainXml, "MediaId");
                String fileNameTag = extractTag(plainXml, "FileName");
                return handleFileMessage(fromUser, toUser, createTime, mediaId, fileNameTag);
            } else if ("event".equals(msgType)) {
                return textReply(fromUser, toUser, createTime,
                        "Welcome! Ask me any question about your documents, or upload a file to index it.");
            } else {
                return textReply(fromUser, toUser, createTime,
                        "I only support text questions and file uploads (PDF, DOCX, XLSX).");
            }
        } catch (Exception e) {
            log.error("Error handling WeCom message", e);
            return textReply("", "", "", "Internal error, please try again later.");
        }
    }

    /**
     * Encrypt reply for WeCom callback.
     */
    public String encryptReply(String plainReply, String timestamp, String nonce) {
        return cryptUtil.encryptReply(plainReply, timestamp, nonce);
    }

    private String handleTextMessage(String fromUser, String toUser, String createTime, String content) {
        if (content == null || content.isBlank()) {
            return textReply(fromUser, toUser, createTime, "Please ask a question.");
        }

        // Strip @bot mention if present
        String question = content.replaceAll("@\\S+\\s*", "").trim();
        if (question.isEmpty()) {
            return textReply(fromUser, toUser, createTime,
                    "How can I help? Ask me any question about the knowledge base.");
        }

        // Find tenant mapping by CorpID
        String tenantId = resolveTenant(props.getCorpId());
        if (tenantId == null) {
            return textReply(fromUser, toUser, createTime,
                    "Bot not yet configured. Please map this WeCom corp to a tenant first.");
        }

        RagChatService.ChatResult result = ragChatService.ask(tenantId, fromUser, question);

        String replyText = result.answer();
        if (!result.sources().isEmpty()) {
            replyText += "\n\n---\nSources: " + String.join(", ", result.sources());
        }

        return textReply(fromUser, toUser, createTime, replyText);
    }

    private String handleFileMessage(String fromUser, String toUser, String createTime,
                                      String mediaId, String fileName) {
        String tenantId = resolveTenant(props.getCorpId());
        if (tenantId == null) {
            return textReply(fromUser, toUser, createTime,
                    "Bot not yet configured. Please map this WeCom corp to a tenant first.");
        }

        // Check file type
        if (fileName == null || !isSupportedFile(fileName)) {
            return textReply(fromUser, toUser, createTime,
                    "Unsupported file type. Supported: PDF, DOCX, XLSX, XLS.");
        }

        try {
            byte[] fileBytes = downloadWeComMedia(mediaId);
            if (fileBytes == null || fileBytes.length == 0) {
                return textReply(fromUser, toUser, createTime, "Failed to download file. Please try again.");
            }

            String fileType = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase();
            String content = documentParser.parse(fileBytes, fileName);
            int chunks = ragDocumentService.indexDocument(tenantId, fileName, fileType, content);

            return textReply(fromUser, toUser, createTime,
                    "Indexed \"" + fileName + "\" — " + chunks + " chunks. You can now ask questions about it.");
        } catch (Exception e) {
            log.error("Failed to index WeCom file: {}", fileName, e);
            return textReply(fromUser, toUser, createTime,
                    "Failed to index file: " + e.getMessage());
        }
    }

    private byte[] downloadWeComMedia(String mediaId) {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            log.warn("Cannot download media: no access token");
            return null;
        }
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/media/get?access_token="
                    + accessToken + "&media_id=" + mediaId;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.error("Failed to download WeCom media {}", mediaId, e);
            return null;
        }
    }

    private String getAccessToken() {
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                    + props.getCorpId() + "&corpsecret=" + System.getenv("WECOM_SECRET");
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            String resp = new String(conn.getInputStream().readAllBytes());
            // Parse simple JSON: {"errcode":0,"errmsg":"ok","access_token":"..."}
            Matcher m = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(resp);
            if (m.find()) return m.group(1);
            log.warn("Failed to get WeCom access_token: {}", resp);
            return null;
        } catch (Exception e) {
            log.error("Failed to get WeCom access_token", e);
            return null;
        }
    }

    private String resolveTenant(String corpId) {
        return mappingRepo.findByPlatformAndPlatformTeamId("wecom", corpId)
                .map(ImTenantMapping::getTenantId)
                .orElse(null);
    }

    private String textReply(String fromUser, String toUser, String createTime, String content) {
        if (fromUser.isEmpty()) fromUser = "user";
        if (toUser.isEmpty()) toUser = props.getCorpId();
        if (createTime.isEmpty()) createTime = String.valueOf(System.currentTimeMillis() / 1000);
        return """
                <xml>
                <ToUserName><![CDATA[%s]]></ToUserName>
                <FromUserName><![CDATA[%s]]></FromUserName>
                <CreateTime>%s</CreateTime>
                <MsgType><![CDATA[text]]></MsgType>
                <Content><![CDATA[%s]]></Content>
                </xml>""".formatted(fromUser, toUser, createTime, content);
    }

    private static boolean isSupportedFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private static String extractTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + "><!\\[CDATA\\[([^\\]]*)\\]\\]></" + tag + ">");
        Matcher m = p.matcher(xml);
        if (m.find()) return m.group(1);
        // Try non-CDATA
        p = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">");
        m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }
}
