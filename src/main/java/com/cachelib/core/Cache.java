package com.cachelib.core;

import java.util.Optional;

public interface Cache<K, V> {

    Optional<V> get(K key);

    void put(K key, V value);

    boolean remove(K key);

    int size();
}
