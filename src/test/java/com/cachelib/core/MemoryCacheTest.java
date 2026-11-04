package com.cachelib.core;

import com.cachelib.builder.CacheBuilder;
import com.cachelib.eviction.FifoEvictionPolicy;
import com.cachelib.listener.RemovalCause;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCacheTest {

    @Test
    void storesAndReadsValues() {
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder().build();
        cache.put("a", 1);
        assertEquals(1, cache.get("a").orElseThrow());
        assertTrue(cache.get("inexistente").isEmpty());
    }

    @Test
    void lruEvictsLeastRecentlyUsedByDefault() {
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder().maxEntries(2).build();
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");
        cache.put("c", 3);

        assertTrue(cache.get("b").isEmpty());
        assertTrue(cache.get("a").isPresent());
        assertTrue(cache.get("c").isPresent());
    }

    @Test
    void fifoPolicyIgnoresReads() {
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .maxEntries(2)
                .evictionPolicy(new FifoEvictionPolicy<>())
                .build();
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");
        cache.put("c", 3);

        assertTrue(cache.get("a").isEmpty());
        assertTrue(cache.get("b").isPresent());
    }

    @Test
    void expiredEntriesAreRemovedAndReported() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        List<RemovalCause> causes = new ArrayList<>();
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .clock(clock)
                .expireAfterWrite(Duration.ofSeconds(10))
                .addListener((k, v, cause) -> causes.add(cause))
                .build();

        cache.put("a", 1);
        clock.advance(Duration.ofSeconds(11));

        assertTrue(cache.get("a").isEmpty());
        assertEquals(List.of(RemovalCause.EXPIRED), causes);
    }

    @Test
    void expiredEntryIsPreferredOverValidEntryWhenFull() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        List<RemovalCause> causes = new ArrayList<>();
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .clock(clock)
                .maxEntries(2)
                .expireAfterWrite(Duration.ofSeconds(10))
                .addListener((k, v, cause) -> causes.add(cause))
                .build();

        cache.put("a", 1);
        clock.advance(Duration.ofSeconds(6));
        cache.put("b", 2);
        clock.advance(Duration.ofSeconds(5));   // "a" expira, "b" ainda vale
        cache.put("c", 3);

        assertEquals(List.of(RemovalCause.EXPIRED), causes);
        assertTrue(cache.get("b").isPresent());
        assertTrue(cache.get("c").isPresent());
    }

    @Test
    void listenerCanReadCacheWithoutDeadlock() {
        Cache<String, Integer>[] holder = new Cache[1];
        holder[0] = CacheBuilder.<String, Integer>newBuilder()
                .addListener((k, v, cause) -> holder[0].size())
                .build();

        holder[0].put("a", 1);
        assertTrue(holder[0].remove("a"));
    }

    @Test
    void explicitRemovalNotifiesListeners() {
        List<RemovalCause> causes = new ArrayList<>();
        Cache<String, Integer> cache = CacheBuilder.<String, Integer>newBuilder()
                .addListener((k, v, cause) -> causes.add(cause))
                .build();

        cache.put("a", 1);
        assertTrue(cache.remove("a"));
        assertEquals(List.of(RemovalCause.EXPLICIT), causes);
    }

    @Test
    void concurrentUseNeverExceedsCapacity() throws InterruptedException {
        int capacity = 50;
        Cache<Integer, Integer> cache = CacheBuilder.<Integer, Integer>newBuilder()
                .maxEntries(capacity)
                .build();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            int offset = t * 10_000;
            pool.submit(() -> {
                for (int i = 0; i < 20_000; i++) {
                    cache.put(offset + i, i);
                    cache.get(offset + i - 1);
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertTrue(cache.size() <= capacity);
    }

    @Test
    void builderRejectsInvalidSize() {
        assertThrows(IllegalArgumentException.class,
                () -> CacheBuilder.<String, Integer>newBuilder().maxEntries(0));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
