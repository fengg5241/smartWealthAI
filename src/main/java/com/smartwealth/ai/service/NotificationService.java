package com.smartwealth.ai.service;

import com.smartwealth.ai.config.WhatsAppProperties;
import com.smartwealth.ai.domain.WaConversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final WhatsAppProperties waProps;

    public NotificationService(JavaMailSender mailSender, WhatsAppProperties waProps) {
        this.mailSender = mailSender;
        this.waProps = waProps;
    }

    public void notifyHighIntent(WaConversation conv, String customerPhone, String message) {
        log.warn("HIGH INTENT: tenant={}, customer={}, message={}", conv.getTenantId(), customerPhone, message);
    }

    public void notifyAiFailed(WaConversation conv, String customerPhone, String question) {
        log.info("AI FAILED: tenant={}, customer={}, question={}", conv.getTenantId(), customerPhone, question);
    }

    public void notifyOutside24h(WaConversation conv, String customerPhone, String message) {
        log.warn("OUTSIDE 24H WINDOW: tenant={}, customer={}, message={}", conv.getTenantId(), customerPhone, message);
    }
}
