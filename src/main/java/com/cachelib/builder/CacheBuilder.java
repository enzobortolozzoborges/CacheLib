package com.cachelib.builder;

import com.cachelib.core.Cache;
import com.cachelib.core.CacheConfig;
import com.cachelib.core.MemoryCache;
import com.cachelib.eviction.EvictionPolicy;
import com.cachelib.eviction.LruEvictionPolicy;
import com.cachelib.listener.CacheListener;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class CacheBuilder<K, V> {

    private static final int DEFAULT_MAX_ENTRIES = 100;

    private int maxEntries = DEFAULT_MAX_ENTRIES;
    private Duration ttl;
    private Clock clock = Clock.systemUTC();
    private EvictionPolicy<K> evictionPolicy = new LruEvictionPolicy<>();
    private final List<CacheListener<K, V>> listeners = new ArrayList<>();

    private CacheBuilder() {
    }

    public static <K, V> CacheBuilder<K, V> newBuilder() {
        return new CacheBuilder<>();
    }

    public CacheBuilder<K, V> maxEntries(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries deve ser positivo");
        }
        this.maxEntries = maxEntries;
        return this;
    }

    public CacheBuilder<K, V> expireAfterWrite(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl deve ser positivo");
        }
        this.ttl = ttl;
        return this;
    }

    public CacheBuilder<K, V> evictionPolicy(EvictionPolicy<K> evictionPolicy) {
        if (evictionPolicy == null) {
            throw new IllegalArgumentException("evictionPolicy não pode ser nula");
        }
        this.evictionPolicy = evictionPolicy;
        return this;
    }

    public CacheBuilder<K, V> addListener(CacheListener<K, V> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener não pode ser nulo");
        }
        listeners.add(listener);
        return this;
    }

    public CacheBuilder<K, V> clock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock não pode ser nulo");
        }
        this.clock = clock;
        return this;
    }

    public Cache<K, V> build() {
        return new MemoryCache<>(new CacheConfig<>(maxEntries, ttl, clock, evictionPolicy, listeners));
    }
}
