package com.cachelib.eviction;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class FifoEvictionPolicy<K> implements EvictionPolicy<K> {

    private final Set<K> insertionOrder = new LinkedHashSet<>();

    @Override
    public void onInsert(K key) {
        insertionOrder.add(key);
    }

    @Override
    public void onAccess(K key) {
        // FIFO ignora leituras por definição.
    }

    @Override
    public void onRemove(K key) {
        insertionOrder.remove(key);
    }

    @Override
    public Optional<K> selectVictim() {
        Iterator<K> it = insertionOrder.iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
    }
}
