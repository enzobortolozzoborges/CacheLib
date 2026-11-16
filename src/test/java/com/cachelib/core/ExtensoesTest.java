package com.cachelib.core;

import com.cachelib.builder.CacheBuilder;
import com.cachelib.eviction.FifoEvictionPolicy;
import com.cachelib.listener.Notifications;
import com.cachelib.listener.RemovalCause;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ExtensoesTest {

    @Test
    void policyCanBeSwappedWhileCacheIsInUse() {
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder().maxEntries(2).build();
        cache.put("a", 1);
        cache.put("b", 2);
        cache.setEvictionPolicy(new FifoEvictionPolicy<>());
        cache.get("a");
        cache.put("c", 3);

        assertTrue(cache.get("a").isEmpty());
        assertTrue(cache.get("b").isPresent());
    }

    @Test
    void entryTtlOverridesDefault() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        List<RemovalCause> causes = new ArrayList<>();
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .clock(clock)
                .addListener((k, v, c) -> causes.add(c))
                .build();

        cache.put("curto", 1, Duration.ofSeconds(2));
        cache.put("longo", 2, Duration.ofMinutes(10));
        clock.advance(Duration.ofSeconds(3));

        assertTrue(cache.get("curto").isEmpty());
        assertTrue(cache.get("longo").isPresent());
        assertEquals(List.of(RemovalCause.EXPIRED), causes);
    }

    @Test
    void nullTtlMeansNoExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .clock(clock)
                .expireAfterWrite(Duration.ofSeconds(5))
                .build();

        cache.put("eterno", 1, null);
        clock.advance(Duration.ofMinutes(1));

        assertTrue(cache.get("eterno").isPresent());
    }

    @Test
    void weightLimitEvictsUntilNewItemFits() {
        List<RemovalCause> causes = new ArrayList<>();
        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .maxWeight(10, (k, v) -> v.length())
                .addListener((k, v, c) -> causes.add(c))
                .build();

        cache.put("a", "aaaa");
        cache.put("b", "bbbbbb");
        cache.put("c", "cc");

        assertTrue(cache.get("a").isEmpty());
        assertTrue(cache.get("b").isPresent());
        assertTrue(cache.get("c").isPresent());
        assertEquals(List.of(RemovalCause.EVICTED), causes);
    }

    @Test
    void itemLargerThanCapacityIsRejected() {
        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .maxWeight(10, (k, v) -> v.length())
                .build();

        assertThrows(IllegalArgumentException.class, () -> cache.put("grande", "x".repeat(11)));
    }

    @Test
    void replacingValueDoesNotNotify() {
        List<RemovalCause> causes = new ArrayList<>();
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .addListener((k, v, c) -> causes.add(c))
                .build();

        cache.put("a", 1);
        cache.put("a", 2);

        assertTrue(causes.isEmpty());
        assertEquals(2, cache.get("a").orElseThrow());
    }

    @Test
    void failingListenerDoesNotBlockOthers() {
        List<String> received = new ArrayList<>();
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .maxEntries(1)
                .addListener((k, v, c) -> {
                    throw new IllegalStateException("falha de propósito");
                })
                .addListener((k, v, c) -> received.add(k))
                .build();

        cache.put("a", 1);
        cache.put("b", 2);

        assertEquals(List.of("a"), received);
    }

    @Test
    void asynchronousNotificationArrivesLater() throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch delivered = new CountDownLatch(1);
            Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                    .notificationStrategy(Notifications.asynchronous(pool))
                    .addListener((k, v, c) -> {
                        if (c == RemovalCause.EXPLICIT) {
                            delivered.countDown();
                        }
                    })
                    .build();

            cache.put("a", 1);
            assertTrue(cache.remove("a"));
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
