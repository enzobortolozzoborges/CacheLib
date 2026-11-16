package com.cachelib.listener;

import java.util.List;

public interface NotificationStrategy<K, V> {

    void dispatch(List<RemovalEvent<K, V>> events, List<CacheListener<K, V>> listeners);
}
