package com.flashsale.checkout.infra;

import com.flashsale.checkout.domain.OrderSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryRedisStore {

    private final String initialStock;
    private final Map<String, AtomicInteger> stock = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<OrderSnapshot>> orderCache = new ConcurrentHashMap<>();

    public InMemoryRedisStore(@Value("${app.stock.initial:PROD-001:10,PROD-002:8}") String initialStock) {
        this.initialStock = initialStock;
        loadInitialStock();
    }

    void loadInitialStock() {
        for (String pair : initialStock.split(",")) {
            String[] values = pair.trim().split(":");
            if (values.length == 2) {
                stock.put(values[0], new AtomicInteger(Integer.parseInt(values[1])));
            }
        }
    }

    public boolean reserveStock(String productId, int quantity) {
        AtomicInteger current = stock.computeIfAbsent(productId, ignored -> new AtomicInteger(0));
        while (true) {
            int available = current.get();
            if (available < quantity) {
                return false;
            }
            if (current.compareAndSet(available, available - quantity)) {
                return true;
            }
        }
    }

    public void releaseStock(String productId, int quantity) {
        stock.computeIfAbsent(productId, ignored -> new AtomicInteger(0)).addAndGet(quantity);
    }

    public int getStock(String productId) {
        return stock.getOrDefault(productId, new AtomicInteger(0)).get();
    }

    public Optional<OrderSnapshot> findOrder(String orderKey) {
        CacheEntry<OrderSnapshot> entry = orderCache.get(orderKey);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            orderCache.remove(orderKey);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void putOrder(String orderKey, OrderSnapshot snapshot, Duration ttl) {
        orderCache.put(orderKey, new CacheEntry<>(snapshot, Instant.now().plus(ttl)));
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }
}

