package com.cachelib.eviction;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class LruEvictionPolicy<K> implements EvictionPolicy<K> {

    private final Set<K> order = new LinkedHashSet<>();

    @Override
    public void onInsert(K key) {
        order.remove(key);
        order.add(key);
    }

    @Override
    public void onAccess(K key) {
        if (order.remove(key)) {
            order.add(key);
        }
    }

    @Override
    public void onRemove(K key) {
        order.remove(key);
    }

    @Override
    public Optional<K> selectVictim() {
        Iterator<K> it = order.iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
    }
}
