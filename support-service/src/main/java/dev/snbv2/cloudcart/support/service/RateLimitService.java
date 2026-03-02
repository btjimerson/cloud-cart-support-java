package dev.snbv2.cloudcart.support.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory sliding-window rate limiter that tracks requests per client.
 *
 * <p>Enforces a maximum of {@value #MAX_REQUESTS} requests per {@value #WINDOW_SECONDS}-second
 * window, keyed by a client identifier (typically a customer ID or session IP). This is a
 * simple, single-instance implementation — it does not coordinate across replicas.</p>
 *
 * <p>In production, rate limiting should be handled at the platform level (e.g., via
 * Agent Gateway) rather than in application code.</p>
 */
@Service
public class RateLimitService {

    static final int MAX_REQUESTS = 20;
    static final int WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    /**
     * Checks whether a request from the given client is allowed under the rate limit.
     *
     * <p>Evicts timestamps older than the sliding window, then checks whether the
     * remaining count is below the maximum. If allowed, records the current timestamp.</p>
     *
     * @param clientId the client identifier (customer ID, IP address, etc.)
     * @return {@code true} if the request is allowed, {@code false} if the rate limit is exceeded
     */
    public boolean tryAcquire(String clientId) {
        Deque<Instant> timestamps = requestLog.computeIfAbsent(clientId, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);

        // Evict expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS) {
            return false;
        }

        timestamps.addLast(Instant.now());
        return true;
    }
}
