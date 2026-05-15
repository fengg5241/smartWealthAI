package com.smartwealth.ai.service;

import com.smartwealth.ai.service.model.ConversationMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionService {

    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionSnapshot openOrCreate(Long userId, String sessionId) {
        String effectiveSessionId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;

        SessionState state = sessions.compute(effectiveSessionId, (key, current) -> {
            if (current == null || !current.userId().equals(userId)) {
                return new SessionState(userId, new ArrayList<>(), Instant.now());
            }
            current.setLastAccessedAt(Instant.now());
            return current;
        });

        return new SessionSnapshot(effectiveSessionId, state.userId(), List.copyOf(state.messages()));
    }

    public void appendUserMessage(String sessionId, String message) {
        SessionState state = require(sessionId);
        state.messages().add(new ConversationMessage("user", message));
        state.setLastAccessedAt(Instant.now());
    }

    public void appendAssistantMessage(String sessionId, String message) {
        SessionState state = require(sessionId);
        state.messages().add(new ConversationMessage("assistant", message));
        state.setLastAccessedAt(Instant.now());
    }

    public SessionSnapshot snapshot(String sessionId) {
        SessionState state = require(sessionId);
        return new SessionSnapshot(sessionId, state.userId(), List.copyOf(state.messages()));
    }

    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.remove(sessionId);
    }

    private SessionState require(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("Chat session not found: " + sessionId);
        }
        return state;
    }

    public record SessionSnapshot(
            String sessionId,
            Long userId,
            List<ConversationMessage> messages
    ) {
    }

    private static final class SessionState {
        private final Long userId;
        private final List<ConversationMessage> messages;
        private Instant lastAccessedAt;

        private SessionState(Long userId, List<ConversationMessage> messages, Instant lastAccessedAt) {
            this.userId = userId;
            this.messages = messages;
            this.lastAccessedAt = lastAccessedAt;
        }

        public Long userId() {
            return userId;
        }

        public List<ConversationMessage> messages() {
            return messages;
        }

        public void setLastAccessedAt(Instant lastAccessedAt) {
            this.lastAccessedAt = lastAccessedAt;
        }
    }
}
