package com.dinhluong.dlmstore.chatbot.ai;

import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Per-instance admission control. No retries, queues, credentials or message contents are retained. */
@Component
public class AiUsageGuard {
    @Value("${chatbot.ai.max-in-flight:8}") private int maxInFlight = 8;
    @Value("${chatbot.ai.max-requests-per-minute:60}") private int maxPerMinute = 60;
    private final AtomicInteger inFlight = new AtomicInteger();
    private long minute;
    private int used;

    public <T> T call(Supplier<T> operation) {
        if (inFlight.incrementAndGet() > maxInFlight) {
            inFlight.decrementAndGet();
            throw unavailable();
        }
        try {
            synchronized (this) {
                long now = System.nanoTime() / 60_000_000_000L;
                if (minute != now) { minute = now; used = 0; }
                if (used >= maxPerMinute) throw unavailable();
                used++;
            }
            return operation.get();
        } finally {
            inFlight.decrementAndGet();
        }
    }
    private static AiProviderException unavailable() {
        return new AiProviderException("Local AI request budget or concurrency limit reached", true, null);
    }
}
