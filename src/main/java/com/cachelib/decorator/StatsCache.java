package com.cachelib.decorator;

import com.cachelib.core.Cache;
import com.cachelib.eviction.EvictionPolicy;
import com.cachelib.stats.CacheStats;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class StatsCache<K, V> implements Cache<K, V> {

    private final Cache<K, V> delegate;
    private final CacheStats<K, V> stats;

    public StatsCache(Cache<K, V> delegate, CacheStats<K, V> stats) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    @Override
    public Optional<V> get(K key) {
        Optional<V> result = delegate.get(key);
        if (result.isPresent()) {
            stats.recordHit();
        } else {
            stats.recordMiss();
        }
        return result;
    }

    @Override
    public void put(K key, V value) {
        delegate.put(key, value);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        delegate.put(key, value, ttl);
    }

    @Override
    public boolean remove(K key) {
        return delegate.remove(key);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public void setEvictionPolicy(EvictionPolicy<K> policy) {
        delegate.setEvictionPolicy(policy);
    }

    public CacheStats<K, V> stats() {
        return stats;
    }
}
