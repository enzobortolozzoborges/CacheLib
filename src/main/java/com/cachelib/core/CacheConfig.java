package com.cachelib.core;

import com.cachelib.eviction.EvictionPolicy;
import com.cachelib.listener.CacheListener;
import com.cachelib.listener.NotificationStrategy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.ToLongBiFunction;

public record CacheConfig<K, V>(
        long capacity,
        ToLongBiFunction<K, V> weigher,
        Duration ttl,
        Clock clock,
        EvictionPolicy<K> evictionPolicy,
        List<CacheListener<K, V>> listeners,
        NotificationStrategy<K, V> notifier) {

    public CacheConfig {
        listeners = List.copyOf(listeners);
    }
}
