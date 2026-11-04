package com.cachelib.listener;

@FunctionalInterface
public interface CacheListener<K, V> {
    void onRemoval(K key, V value, RemovalCause cause);
}
