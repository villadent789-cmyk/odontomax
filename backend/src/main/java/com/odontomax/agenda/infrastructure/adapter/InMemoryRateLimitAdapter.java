package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.RateLimitPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryRateLimitAdapter implements RateLimitPort {

    private static final long WINDOW_SECONDS = 600; // 10 minutos
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    private static class RateLimitEntry {
        final AtomicInteger count = new AtomicInteger(1);
        final Instant resetTime;

        RateLimitEntry(long windowSeconds) {
            this.resetTime = Instant.now().plusSeconds(windowSeconds);
        }
    }

    @Override
    public boolean isRateLimited(String key, int maxRequests) {
        return isRateLimited(key, maxRequests, WINDOW_SECONDS);
    }

    @Override
    public boolean isRateLimited(String key, int maxRequests, long windowSeconds) {
        Instant now = Instant.now();
        RateLimitEntry entry = rateLimitMap.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.resetTime)) {
                return new RateLimitEntry(windowSeconds);
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return entry.count.get() > maxRequests;
    }

    @Scheduled(fixedRate = 60000)
    public void purgeExpiredEntries() {
        Instant now = Instant.now();
        rateLimitMap.entrySet().removeIf(entry -> now.isAfter(entry.getValue().resetTime));
    }
}
