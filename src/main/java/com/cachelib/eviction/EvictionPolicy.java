package com.cachelib.eviction;

import java.util.Optional;

public interface EvictionPolicy<K> {

    void onInsert(K key);

    void onAccess(K key);

    void onRemove(K key);

    Optional<K> selectVictim();
}
