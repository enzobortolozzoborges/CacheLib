package com.cachelib.decorator;

import com.cachelib.builder.CacheBuilder;
import com.cachelib.core.Cache;
import com.cachelib.stats.CacheStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatsCacheTest {

    @Test
    void countsHitsAndMisses() {
        CacheStats<String, Integer> stats = new CacheStats<>();
        Cache<String, Integer> cache = new StatsCache<>(
                CacheBuilder.<String, Integer>newBuilder().build(), stats);

        cache.put("a", 1);
        cache.get("a");
        cache.get("x");

        assertEquals(1, stats.hits());
        assertEquals(1, stats.misses());
        assertEquals(0.5, stats.hitRate(), 0.0001);
    }

    @Test
    void countsEvictionsWhenStatsIsListener() {
        CacheStats<String, Integer> stats = new CacheStats<>();
        Cache<String, Integer> cache = new StatsCache<>(
                CacheBuilder.<String, Integer>newBuilder().maxEntries(1).addListener(stats).build(), stats);

        cache.put("a", 1);
        cache.put("b", 2);

        assertEquals(1, stats.evictions());
    }
}
