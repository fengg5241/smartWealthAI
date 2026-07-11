package com.smartwealth.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SseEmitterPool {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterPool.class);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicLong eventIdCounter = new AtomicLong(0);

    public void add(String tenantId, SseEmitter emitter) {
        emitters.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.debug("SSE emitter added for tenant={}, total={}", tenantId, count(tenantId));
    }

    public void remove(String tenantId, SseEmitter emitter) {
        var list = emitters.get(tenantId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(tenantId);
        }
        log.debug("SSE emitter removed for tenant={}, remaining={}", tenantId, count(tenantId));
    }

    /**
     * Push event to all online agents for a tenant.
     * Uses monotonically increasing event ID for Last-Event-ID reconnect.
     */
    public void push(String tenantId, Object event) {
        var list = emitters.get(tenantId);
        if (list == null || list.isEmpty()) return;

        long eventId = eventIdCounter.incrementAndGet();
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().id(String.valueOf(eventId)).data(event));
            } catch (IOException ex) {
                remove(tenantId, e);
            }
        }
    }

    public int count(String tenantId) {
        var list = emitters.get(tenantId);
        return list != null ? list.size() : 0;
    }

    public long nextEventId() {
        return eventIdCounter.incrementAndGet();
    }
}
