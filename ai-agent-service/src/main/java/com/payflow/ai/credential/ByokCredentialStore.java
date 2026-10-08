package com.payflow.ai.credential;

import com.payflow.ai.config.AiProperties;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmCredential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, user-scoped, short-TTL store for BYOK LLM credentials. Keys live
 * here and nowhere else: never logged, never persisted to Postgres, never
 * exposed through actuator, never returned to the browser. Entries expire after
 * {@link AiProperties#getByokTtlMinutes()} and are evicted lazily on read plus by
 * a scheduled sweep.
 */
@Component
public class ByokCredentialStore {

    private record Entry(LlmCredential credential, Instant expiresAt) {}

    private final ConcurrentHashMap<java.util.UUID, Map<AiProvider, Entry>> store = new ConcurrentHashMap<>();
    private final AiProperties properties;
    private final Clock clock;

    @Autowired
    public ByokCredentialStore(AiProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ByokCredentialStore(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void store(java.util.UUID userId, LlmCredential credential) {
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(Math.max(0, properties.getByokTtlMinutes())));
        store.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(credential.provider(), new Entry(credential, expiresAt));
    }

    public Optional<LlmCredential> get(java.util.UUID userId, AiProvider provider) {
        Map<AiProvider, Entry> byProvider = store.get(userId);
        if (byProvider == null) {
            return Optional.empty();
        }
        Entry entry = byProvider.get(provider);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            byProvider.remove(provider, entry);
            return Optional.empty();
        }
        return Optional.of(entry.credential());
    }

    public void remove(java.util.UUID userId, AiProvider provider) {
        Map<AiProvider, Entry> byProvider = store.get(userId);
        if (byProvider != null) {
            byProvider.remove(provider);
            if (byProvider.isEmpty()) {
                store.remove(userId, byProvider);
            }
        }
    }

    public void removeAll(java.util.UUID userId) {
        store.remove(userId);
    }

    public List<AiProvider> providers(java.util.UUID userId) {
        Map<AiProvider, Entry> byProvider = store.get(userId);
        if (byProvider == null) {
            return List.of();
        }
        List<AiProvider> result = new ArrayList<>();
        for (Map.Entry<AiProvider, Entry> e : byProvider.entrySet()) {
            if (!isExpired(e.getValue())) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    public Instant expiresAt(java.util.UUID userId, AiProvider provider) {
        Map<AiProvider, Entry> byProvider = store.get(userId);
        if (byProvider == null) {
            return null;
        }
        Entry entry = byProvider.get(provider);
        if (entry == null || isExpired(entry)) {
            return null;
        }
        return entry.expiresAt();
    }

    /** Evicts expired entries so keys never linger past their TTL even if never read again. */
    @Scheduled(fixedRateString = "PT5M")
    public void sweep() {
        for (Map.Entry<java.util.UUID, Map<AiProvider, Entry>> userEntry : store.entrySet()) {
            Map<AiProvider, Entry> byProvider = userEntry.getValue();
            byProvider.values().removeIf(this::isExpired);
            if (byProvider.isEmpty()) {
                store.remove(userEntry.getKey(), byProvider);
            }
        }
    }

    private boolean isExpired(Entry entry) {
        return !clock.instant().isBefore(entry.expiresAt());
    }
}
