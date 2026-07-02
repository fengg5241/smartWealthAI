package com.smartwealth.ai.service;

import com.smartwealth.ai.config.WeComProperties;
import com.smartwealth.ai.domain.GoodPhrase;
import com.smartwealth.ai.repository.ImTenantMappingRepository;
import com.smartwealth.ai.domain.ImTenantMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    // Learning assistant services
    private final GoodPhraseService phraseService;
    private final LearningAIService learningAI;
    private final OcrService ocrService;
    // Session state: track user entry mode (for multi-turn: prefix then image)
    private final Map<String, String> userSessionMode = new ConcurrentHashMap<>();

    public WeComBotService(WeComProperties props, ImTenantMappingRepository mappingRepo,
                           RagChatService ragChatService, DocumentParserService documentParser,
                           RagDocumentService ragDocumentService,
                           GoodPhraseService phraseService,
                           LearningAIService learningAI,
                           OcrService ocrService) {
        this.props = props;
        this.cryptUtil = new WeComCryptUtil(props.getToken(), props.getEncodingAesKey(), props.getCorpId());
        this.mappingRepo = mappingRepo;
        this.ragChatService = ragChatService;
        this.documentParser = documentParser;
        this.ragDocumentService = ragDocumentService;
        this.phraseService = phraseService;
        this.learningAI = learningAI;
        this.ocrService = ocrService;
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
            } else if ("image".equals(msgType)) {
                String mediaId = extractTag(plainXml, "MediaId");
                String picUrl = extractTag(plainXml, "PicUrl");
                return handleImageMessage(fromUser, toUser, createTime, mediaId, picUrl);
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

        String tenantId = resolveTenant(props.getCorpId());
        if (tenantId == null) {
            return textReply(fromUser, toUser, createTime,
                    "Bot not yet configured. Please map this WeCom corp to a tenant first.");
        }

        // Strip @bot mention
        String text = content.replaceAll("@\\S+\\s*", "").trim();
        if (text.isEmpty()) {
            return textReply(fromUser, toUser, createTime,
                    "How can I help? Send '录入错题' to enter mistake mode, '录入好句：+ 内容' to add a phrase, '查找错题 + keyword' to search, or ask any question.");
        }

        // --- Learning commands ---

        // Phrase entry: 录入好句：xxx
        if (text.startsWith("录入好句：") || text.startsWith("录入好句:")) {
            String phraseText = text.substring(text.indexOf('：') >= 0 ? text.indexOf('：') + 1 : text.indexOf(':') + 1).trim();
            if (phraseText.isEmpty()) return textReply(fromUser, toUser, createTime, "请输入好句内容，如：录入好句：落霞与孤鹜齐飞");

            LearningAIService.PhraseTags tags = learningAI.tagPhrase(phraseText);
            GoodPhraseService.PhraseInput input = new GoodPhraseService.PhraseInput();
            input.content = phraseText;
            input.theme = tags.theme();
            input.emotion = tags.emotion();
            input.usageType = tags.usageType();
            input.tags = tags.tags();
            GoodPhrase saved = phraseService.create(tenantId, input, null, null);

            String reply = "好句已录入 📝\n原文：" + phraseText
                    + "\n主题：" + saved.getTheme() + " | 情感：" + saved.getEmotion()
                    + " | 用途：" + saved.getUsageType()
                    + "\n标签：" + (saved.getTags() != null ? saved.getTags() : "");
            return textReply(fromUser, toUser, createTime, reply);
        }

        // Mistake entry mode: 录入错题 (sets session mode, next image will be processed)
        if (text.startsWith("录入错题")) {
            userSessionMode.put(fromUser, "mistake-entry");
            return textReply(fromUser, toUser, createTime,
                    "已进入错题录入模式。请发送错题图片，或发送文字\"取消\"退出。");
        }

        // Cancel entry mode
        if ("取消".equals(text) || "cancel".equalsIgnoreCase(text)) {
            userSessionMode.remove(fromUser);
            return textReply(fromUser, toUser, createTime, "已退出录入模式。");
        }

        // Search mistakes: 查找错题 xxx
        if (text.startsWith("查找错题") || text.startsWith("找错题")) {
            String query = text.replaceFirst("查找错题|找错题", "").trim();
            if (query.isEmpty()) query = "错题";
            List<String> results = searchMistakesByText(tenantId, query);
            if (results.isEmpty()) {
                return textReply(fromUser, toUser, createTime, "未找到匹配的错题。试试：" + query);
            }
            return textReply(fromUser, toUser, createTime,
                    "找到以下错题：\n" + String.join("\n", results));
        }

        // Search phrases: 查找好句 xxx
        if (text.startsWith("查找好句") || text.startsWith("找好句")) {
            String keyword = text.replaceFirst("查找好句|找好句", "").trim();
            return textReply(fromUser, toUser, createTime,
                    "请在网页端浏览好词好句库，支持按主题/情感筛选。");
        }

        // Review status: 复习
        if ("复习".equals(text) || "今日复习".equals(text)) {
            // For review status, we need ReviewService — skip for now, direct to web
            return textReply(fromUser, toUser, createTime,
                    "请在网页端「复习」页面查看今日待复习错题。");
        }

        // Help
        if ("帮助".equals(text) || "help".equalsIgnoreCase(text)) {
            return textReply(fromUser, toUser, createTime,
                    "支持的命令：\n" +
                    "• 录入错题 — 进入错题录入模式\n" +
                    "• 录入好句：内容 — 直接添加好词好句\n" +
                    "• 查找错题 关键词 — 搜索错题\n" +
                    "• 取消 — 退出当前模式\n" +
                    "• 任何其他文字 — AI 问答");
        }

        // --- Default: RAG Q&A ---
        RagChatService.ChatResult result = ragChatService.ask(tenantId, fromUser, text);

        String replyText = result.answer();
        if (!result.sources().isEmpty()) {
            replyText += "\n\n---\nSources: " + String.join(", ", result.sources());
        }

        return textReply(fromUser, toUser, createTime, replyText);
    }

    private String handleImageMessage(String fromUser, String toUser, String createTime,
                                       String mediaId, String picUrl) {
        String tenantId = resolveTenant(props.getCorpId());
        if (tenantId == null) {
            return textReply(fromUser, toUser, createTime,
                    "Bot not yet configured.");
        }

        String mode = userSessionMode.getOrDefault(fromUser, "");

        // If in mistake entry mode, download image and process
        if ("mistake-entry".equals(mode)) {
            try {
                byte[] imageBytes = downloadWeComMedia(mediaId);
                if (imageBytes == null || imageBytes.length == 0) {
                    return textReply(fromUser, toUser, createTime,
                            "图片下载失败，请重试。");
                }

                String ocrText = ocrService.ocrImage(imageBytes);
                LearningAIService.MistakeClassification cls =
                        learningAI.classifyMistake(
                                ocrText.isBlank() ? null : ocrText, imageBytes);

                userSessionMode.remove(fromUser);

                return textReply(fromUser, toUser, createTime,
                        "错题识别完成 📋\n" +
                        "科目：" + cls.subject() + "\n" +
                        "题型：" + cls.questionType() + "\n" +
                        "错因：" + cls.errorReason() + "\n" +
                        "建议答案：" + cls.suggestedAnswer() + "\n\n" +
                        "请到网页端确认并补充年级、来源后入库。");
            } catch (Exception e) {
                log.error("Mistake entry processing failed", e);
                return textReply(fromUser, toUser, createTime,
                        "错题处理失败：" + e.getMessage());
            }
        }

        return textReply(fromUser, toUser, createTime,
                "收到图片。请先发送「录入错题」进入录入模式，或发送「帮助」查看所有命令。");
    }

    private List<String> searchMistakesByText(String tenantId, String query) {
        return List.of("请在网页端「错题库」页面搜索：\"" + query + "\"");
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
