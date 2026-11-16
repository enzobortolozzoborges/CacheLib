package com.cachelib.eviction;

import com.cachelib.examples.RandomEvictionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvictionPolicyFactoryTest {

    @Test
    void createsBuiltInPoliciesByName() {
        assertInstanceOf(LruEvictionPolicy.class, EvictionPolicyFactory.<String>create("lru"));
        assertInstanceOf(FifoEvictionPolicy.class, EvictionPolicyFactory.<String>create("FIFO"));
    }

    @Test
    void createsClientRegisteredPolicy() {
        EvictionPolicyFactory.register("random", RandomEvictionPolicy::new);
        assertInstanceOf(RandomEvictionPolicy.class, EvictionPolicyFactory.<String>create("random"));
    }

    @Test
    void rejectsUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> EvictionPolicyFactory.<String>create("lfu"));
    }
}
