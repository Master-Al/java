package org.acme.orders.service;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.acme.orders.model.OrderRequest;
import org.acme.orders.model.OrderResult;
import org.acme.orders.model.OrderStatus;

@ApplicationScoped
public class OrderService {

    private final ConcurrentMap<UUID, OrderStatus> statuses = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, OrderResult> results = new ConcurrentHashMap<>();

    private final ExecutorService executor;

    /**
     * Creates a single background worker used for asynchronous order processing.
     */
    public OrderService() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("order-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    /**
     * Enqueues a new order and returns its job id immediately.
     */
    public UUID submit(OrderRequest request) {
        UUID jobId = UUID.randomUUID();
        statuses.put(jobId, OrderStatus.QUEUED);

        executor.submit(() -> process(jobId, request));
        return jobId;
    }

    /**
     * Reads the current status for a job id.
     */
    public OrderStatus getStatus(UUID jobId) {
        return statuses.get(jobId);
    }

    /**
     * Reads the result for a job id when processing has completed.
     */
    public OrderResult getResult(UUID jobId) {
        return results.get(jobId);
    }

    /**
     * Performs the actual business work on the background thread.
     */
    private void process(UUID jobId, OrderRequest request) {
        try {
            statuses.put(jobId, OrderStatus.PROCESSING);

            OrderResult result = new OrderResult();
            result.jobId = jobId;
            result.totalPrice = request.quantity * request.unitPrice;
            result.processedAt = Instant.now();
            result.message = "Order processed for customer " + request.customerId;

            results.put(jobId, result);
            statuses.put(jobId, OrderStatus.COMPLETED);
        } catch (Exception e) {
            statuses.put(jobId, OrderStatus.FAILED);
        }
    }

    /**
     * Stops the background executor when the application is shutting down.
     */
    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
