package dev.snbv2.cloudcart.support.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    void allowsRequestsUnderLimit() {
        for (int i = 0; i < RateLimitService.MAX_REQUESTS; i++) {
            assertThat(rateLimitService.tryAcquire("client-1")).isTrue();
        }
    }

    @Test
    void rejectsRequestsOverLimit() {
        for (int i = 0; i < RateLimitService.MAX_REQUESTS; i++) {
            rateLimitService.tryAcquire("client-1");
        }
        assertThat(rateLimitService.tryAcquire("client-1")).isFalse();
    }

    @Test
    void tracksClientsIndependently() {
        for (int i = 0; i < RateLimitService.MAX_REQUESTS; i++) {
            rateLimitService.tryAcquire("client-1");
        }
        // client-2 should still be allowed
        assertThat(rateLimitService.tryAcquire("client-2")).isTrue();
    }
}
