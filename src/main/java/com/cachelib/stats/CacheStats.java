package com.cachelib.stats;

import com.cachelib.listener.CacheListener;
import com.cachelib.listener.RemovalCause;

import java.util.concurrent.atomic.AtomicLong;

public final class CacheStats<K, V> implements CacheListener<K, V> {

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong expirations = new AtomicLong();
    private final AtomicLong explicitRemovals = new AtomicLong();

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    @Override
    public void onRemoval(K key, V value, RemovalCause cause) {
        switch (cause) {
            case EVICTED -> evictions.incrementAndGet();
            case EXPIRED -> expirations.incrementAndGet();
            case EXPLICIT -> explicitRemovals.incrementAndGet();
        }
    }

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public long evictions() { return evictions.get(); }
    public long expirations() { return expirations.get(); }
    public long explicitRemovals() { return explicitRemovals.get(); }

    public double hitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    @Override
    public String toString() {
        return String.format("hits=%d misses=%d taxaDeAcerto=%.2f evictions=%d expirations=%d",
                hits(), misses(), hitRate(), evictions(), expirations());
    }
}
