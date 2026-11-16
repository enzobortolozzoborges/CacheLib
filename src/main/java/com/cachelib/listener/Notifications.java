package com.cachelib.listener;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class Notifications {

    private static final System.Logger LOG = System.getLogger(Notifications.class.getName());

    private Notifications() {
    }

    public static <K, V> NotificationStrategy<K, V> synchronous() {
        return Notifications::deliverAll;
    }

    public static <K, V> NotificationStrategy<K, V> asynchronous(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return (events, listeners) -> executor.execute(() -> deliverAll(events, listeners));
    }

    private static <K, V> void deliverAll(List<RemovalEvent<K, V>> events, List<CacheListener<K, V>> listeners) {
        for (RemovalEvent<K, V> event : events) {
            for (CacheListener<K, V> listener : listeners) {
                deliver(event, listener);
            }
        }
    }

    private static <K, V> void deliver(RemovalEvent<K, V> event, CacheListener<K, V> listener) {
        try {
            listener.onRemoval(event.key(), event.value(), event.cause());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Listener falhou ao receber a remoção de " + event.key() + " (" + event.cause() + ")", e);
        }
    }
}
