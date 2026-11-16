package com.cachelib.core;

import com.cachelib.eviction.EvictionPolicy;

import java.time.Duration;
import java.util.Optional;

public interface Cache<K, V> {

    Optional<V> get(K key);

    void put(K key, V value);

    void put(K key, V value, Duration ttl);

    boolean remove(K key);

    int size();

    void setEvictionPolicy(EvictionPolicy<K> policy);
}
