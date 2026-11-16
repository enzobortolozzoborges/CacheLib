package com.cachelib.listener;

public record RemovalEvent<K, V>(K key, V value, RemovalCause cause) {
}
