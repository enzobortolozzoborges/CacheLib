package com.cachelib.core;

import com.cachelib.eviction.EvictionPolicy;
import com.cachelib.listener.CacheListener;
import com.cachelib.listener.NotificationStrategy;
import com.cachelib.listener.RemovalCause;
import com.cachelib.listener.RemovalEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToLongBiFunction;

public final class MemoryCache<K, V> implements Cache<K, V> {

    private final Object lock = new Object();

    private final Map<K, StoredValue<V>> entries = new LinkedHashMap<>();

    private final long capacity;
    private final ToLongBiFunction<K, V> weigher;
    private final Duration defaultTtl;
    private final Clock clock;
    private final NotificationStrategy<K, V> notifier;
    private final List<CacheListener<K, V>> listeners;

    private EvictionPolicy<K> evictionPolicy;
    private long usedCapacity;

    public MemoryCache(CacheConfig<K, V> config) {
        this.capacity = config.capacity();
        this.weigher = config.weigher();
        this.defaultTtl = config.ttl();
        this.clock = config.clock();
        this.evictionPolicy = config.evictionPolicy();
        this.notifier = config.notifier();
        this.listeners = config.listeners();
    }

    @Override
    public Optional<V> get(K key) {
        return new Operation<Optional<V>>() {
            @Override
            protected Optional<V> execute(List<RemovalEvent<K, V>> pending) {
                StoredValue<V> stored = entries.get(key);
                if (stored == null) {
                    return Optional.empty();
                }
                if (stored.isExpired(clock.instant())) {
                    removeLocked(key, RemovalCause.EXPIRED, pending);
                    return Optional.empty();
                }
                evictionPolicy.onAccess(key);
                return Optional.of(stored.value());
            }
        }.run();
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtl);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("ttl deve ser positivo ou null");
        }
        long weight = weigher.applyAsLong(key, value);
        if (weight <= 0 || weight > capacity) {
            throw new IllegalArgumentException("peso " + weight + " inválido para capacidade " + capacity);
        }

        new Operation<Void>() {
            @Override
            protected Void execute(List<RemovalEvent<K, V>> pending) {
                Instant now = clock.instant();

                StoredValue<V> old = entries.remove(key);
                if (old != null) {
                    usedCapacity -= old.weight();
                    evictionPolicy.onRemove(key);
                }

                makeRoomFor(weight, now, pending);

                Instant expiresAt = ttl == null ? null : now.plus(ttl);
                entries.put(key, new StoredValue<>(value, weight, expiresAt));
                usedCapacity += weight;
                evictionPolicy.onInsert(key);
                return null;
            }
        }.run();
    }

    @Override
    public boolean remove(K key) {
        return new Operation<Boolean>() {
            @Override
            protected Boolean execute(List<RemovalEvent<K, V>> pending) {
                return removeLocked(key, RemovalCause.EXPLICIT, pending);
            }
        }.run();
    }

    @Override
    public int size() {
        return new Operation<Integer>() {
            @Override
            protected Integer execute(List<RemovalEvent<K, V>> pending) {
                return entries.size();
            }
        }.run();
    }

    @Override
    public void setEvictionPolicy(EvictionPolicy<K> newPolicy) {
        Objects.requireNonNull(newPolicy, "policy");
        new Operation<Void>() {
            @Override
            protected Void execute(List<RemovalEvent<K, V>> pending) {
                for (K key : entries.keySet()) {
                    newPolicy.onInsert(key);
                }
                evictionPolicy = newPolicy;
                return null;
            }
        }.run();
    }

    private abstract class Operation<R> {

        final R run() {
            List<RemovalEvent<K, V>> pending = new ArrayList<>();
            R result;
            synchronized (lock) {
                result = execute(pending);
            }
            if (!pending.isEmpty()) {
                notifier.dispatch(pending, listeners);
            }
            return result;
        }

        protected abstract R execute(List<RemovalEvent<K, V>> pending);
    }

    private void makeRoomFor(long weight, Instant now, List<RemovalEvent<K, V>> pending) {
        if (usedCapacity + weight <= capacity) {
            return;
        }

        removeExpiredLocked(now, pending);
        while (usedCapacity + weight > capacity) {
            K victim = evictionPolicy.selectVictim()
                    .orElseThrow(() -> new IllegalStateException("política não indicou vítima com cache cheio"));
            if (!removeLocked(victim, RemovalCause.EVICTED, pending)) {
                throw new IllegalStateException("política indicou uma chave ausente: " + victim);
            }
        }
    }

    private boolean removeLocked(K key, RemovalCause cause, List<RemovalEvent<K, V>> pending) {
        StoredValue<V> stored = entries.remove(key);
        if (stored == null) {
            return false;
        }
        usedCapacity -= stored.weight();
        evictionPolicy.onRemove(key);
        pending.add(new RemovalEvent<>(key, stored.value(), cause));
        return true;
    }

    private void removeExpiredLocked(Instant now, List<RemovalEvent<K, V>> pending) {
        Iterator<Map.Entry<K, StoredValue<V>>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, StoredValue<V>> candidate = it.next();
            StoredValue<V> stored = candidate.getValue();
            if (stored.isExpired(now)) {
                it.remove();
                usedCapacity -= stored.weight();
                evictionPolicy.onRemove(candidate.getKey());
                pending.add(new RemovalEvent<>(candidate.getKey(), stored.value(), RemovalCause.EXPIRED));
            }
        }
    }

    private record StoredValue<T>(T value, long weight, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }
}
