package com.cachelib.eviction;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class EvictionPolicyFactory {

    private static final Map<String, Supplier<EvictionPolicy<?>>> REGISTRY = new ConcurrentHashMap<>();

    static {
        register("lru", LruEvictionPolicy::new);
        register("fifo", FifoEvictionPolicy::new);
    }

    private EvictionPolicyFactory() {
    }

    public static void register(String name, Supplier<EvictionPolicy<?>> supplier) {
        if (name == null || name.isBlank() || supplier == null) {
            throw new IllegalArgumentException("nome e fábrica são obrigatórios");
        }
        REGISTRY.put(normalize(name), supplier);
    }

    @SuppressWarnings("unchecked")
    public static <K> EvictionPolicy<K> create(String name) {
        Supplier<EvictionPolicy<?>> supplier = REGISTRY.get(normalize(name));
        if (supplier == null) {
            throw new IllegalArgumentException("política desconhecida: " + name);
        }
        return (EvictionPolicy<K>) supplier.get();
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
