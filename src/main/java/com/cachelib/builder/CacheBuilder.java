package com.cachelib.builder;

import com.cachelib.core.Cache;
import com.cachelib.core.CacheConfig;
import com.cachelib.core.MemoryCache;
import com.cachelib.eviction.EvictionPolicy;
import com.cachelib.eviction.LruEvictionPolicy;
import com.cachelib.listener.CacheListener;
import com.cachelib.listener.NotificationStrategy;
import com.cachelib.listener.Notifications;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongBiFunction;

public final class CacheBuilder<K, V> {

    private static final long DEFAULT_CAPACITY = 100;

    private long capacity = DEFAULT_CAPACITY;
    private ToLongBiFunction<K, V> weigher = (k, v) -> 1L;
    private Duration ttl;
    private Clock clock = Clock.systemUTC();
    private EvictionPolicy<K> evictionPolicy = new LruEvictionPolicy<>();
    private NotificationStrategy<K, V> notifier = Notifications.synchronous();
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
        this.capacity = maxEntries;
        this.weigher = (k, v) -> 1L;
        return this;
    }

    public CacheBuilder<K, V> maxWeight(long maxWeight, ToLongBiFunction<K, V> weigher) {
        if (maxWeight <= 0) {
            throw new IllegalArgumentException("maxWeight deve ser positivo");
        }
        if (weigher == null) {
            throw new IllegalArgumentException("weigher não pode ser nulo");
        }
        this.capacity = maxWeight;
        this.weigher = weigher;
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

    public CacheBuilder<K, V> notificationStrategy(NotificationStrategy<K, V> notifier) {
        if (notifier == null) {
            throw new IllegalArgumentException("notificationStrategy não pode ser nula");
        }
        this.notifier = notifier;
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
        return new MemoryCache<>(new CacheConfig<>(
                capacity, weigher, ttl, clock, evictionPolicy, listeners, notifier));
    }
}
