package com.cachelib.core;

import com.cachelib.eviction.EvictionPolicy;
import com.cachelib.listener.CacheListener;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public record CacheConfig<K, V>(
        int maxEntries,
        Duration ttl,
        Clock clock,
        EvictionPolicy<K> evictionPolicy,
        List<CacheListener<K, V>> listeners) {

    public CacheConfig {
        listeners = List.copyOf(listeners);
    }
}
