package com.cachelib.examples;

import com.cachelib.eviction.EvictionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class RandomEvictionPolicy<K> implements EvictionPolicy<K> {

    private final List<K> keys = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void onInsert(K key) {
        if (!keys.contains(key)) {
            keys.add(key);
        }
    }

    @Override
    public void onAccess(K key) {

    }

    @Override
    public void onRemove(K key) {
        keys.remove(key);
    }

    @Override
    public Optional<K> selectVictim() {
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(keys.get(random.nextInt(keys.size())));
    }
}
