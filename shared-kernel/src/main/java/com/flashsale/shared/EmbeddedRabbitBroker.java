package com.flashsale.shared;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public final class EmbeddedRabbitBroker {

    private final Map<String, BlockingQueue<Object>> queues = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Object>>> consumers = new ConcurrentHashMap<>();
    private final ExecutorService dispatcher = Executors.newCachedThreadPool();

    private static final EmbeddedRabbitBroker INSTANCE = new EmbeddedRabbitBroker();

    private EmbeddedRabbitBroker() {
    }

    public static EmbeddedRabbitBroker getInstance() {
        return INSTANCE;
    }

    public void publish(String queueName, Object event) {
        var queue = queues.computeIfAbsent(queueName, ignored -> new LinkedBlockingQueue<>());
        queue.offer(event);
        dispatch(queueName);
    }

    public void subscribe(String queueName, Consumer<Object> consumer) {
        consumers.computeIfAbsent(queueName, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
        dispatch(queueName);
    }

    private void dispatch(String queueName) {
        var queue = queues.computeIfAbsent(queueName, ignored -> new LinkedBlockingQueue<>());
        var queueConsumers = consumers.getOrDefault(queueName, List.of());

        if (queueConsumers.isEmpty()) {
            return;
        }

        Object event;
        while ((event = queue.poll()) != null) {
            var message = event;
            for (Consumer<Object> consumer : queueConsumers) {
                dispatcher.submit(() -> consumer.accept(message));
            }
        }
    }
}

