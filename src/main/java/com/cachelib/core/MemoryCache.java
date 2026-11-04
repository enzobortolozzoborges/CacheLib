package com.cachelib.core;

import com.cachelib.eviction.EvictionPolicy;
import com.cachelib.listener.CacheListener;
import com.cachelib.listener.RemovalCause;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemoryCache<K, V> implements Cache<K, V> {

    private final Object lock = new Object();
    private final Map<K, StoredValue<V>> entries = new HashMap<>();

    private final int maxEntries;
    private final Duration ttl;
    private final Clock clock;
    private final EvictionPolicy<K> evictionPolicy;
    private final List<CacheListener<K, V>> listeners;

    public MemoryCache(CacheConfig<K, V> config) {
        this.maxEntries = config.maxEntries();
        this.ttl = config.ttl();
        this.clock = config.clock();
        this.evictionPolicy = config.evictionPolicy();
        this.listeners = config.listeners();
    }

    @Override
    public Optional<V> get(K key) {
        List<Removal<K, V>> pending = new ArrayList<>();
        Optional<V> result;

        synchronized (lock) {
            StoredValue<V> stored = entries.get(key);
            if (stored == null) {
                result = Optional.empty();
            } else if (stored.isExpired(clock.instant())) {
                removeLocked(key, RemovalCause.EXPIRED, pending);
                result = Optional.empty();
            } else {
                evictionPolicy.onAccess(key);
                result = Optional.of(stored.value());
            }
        }

        notifyListeners(pending);
        return result;
    }

    @Override
    public void put(K key, V value) {
        List<Removal<K, V>> pending = new ArrayList<>();

        synchronized (lock) {
            Instant now = clock.instant();

            if (!entries.containsKey(key) && entries.size() >= maxEntries) {
                removeExpiredLocked(now, pending);
                if (entries.size() >= maxEntries) {
                    evictionPolicy.selectVictim()
                            .ifPresent(victim -> removeLocked(victim, RemovalCause.EVICTED, pending));
                }
            }

            Instant expiresAt = ttl == null ? null : now.plus(ttl);
            entries.put(key, new StoredValue<>(value, expiresAt));
            evictionPolicy.onInsert(key);
        }

        notifyListeners(pending);
    }

    @Override
    public boolean remove(K key) {
        List<Removal<K, V>> pending = new ArrayList<>();
        boolean removed;

        synchronized (lock) {
            removed = removeLocked(key, RemovalCause.EXPLICIT, pending);
        }

        notifyListeners(pending);
        return removed;
    }

    @Override
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private boolean removeLocked(K key, RemovalCause cause, List<Removal<K, V>> pending) {
        StoredValue<V> stored = entries.remove(key);
        evictionPolicy.onRemove(key);
        if (stored == null) {
            return false;
        }
        pending.add(new Removal<>(key, stored.value(), cause));
        return true;
    }

    private void removeExpiredLocked(Instant now, List<Removal<K, V>> pending) {
        Iterator<Map.Entry<K, StoredValue<V>>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, StoredValue<V>> candidate = it.next();
            if (candidate.getValue().isExpired(now)) {
                it.remove();
                evictionPolicy.onRemove(candidate.getKey());
                pending.add(new Removal<>(candidate.getKey(), candidate.getValue().value(), RemovalCause.EXPIRED));
            }
        }
    }

    private void notifyListeners(List<Removal<K, V>> pending) {
        for (Removal<K, V> removal : pending) {
            for (CacheListener<K, V> listener : listeners) {
                listener.onRemoval(removal.key(), removal.value(), removal.cause());
            }
        }
    }

    private record StoredValue<T>(T value, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }

    private record Removal<T, U>(T key, U value, RemovalCause cause) {
    }
}
