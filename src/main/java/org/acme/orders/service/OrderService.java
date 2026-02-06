package org.acme.orders.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.acme.orders.model.OrderRequest;
import org.acme.orders.model.OrderResult;
import org.acme.orders.model.OrderStatus;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    private final ConcurrentMap<UUID, OrderStatus> statuses = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, OrderResult> results = new ConcurrentHashMap<>();

    @Inject
    ProducerTemplate producerTemplate;

    /**
     * Enqueues a new order into the Camel route and returns its job id immediately.
     */
    public UUID submit(OrderRequest request) {
        UUID jobId = UUID.randomUUID();
        statuses.put(jobId, OrderStatus.QUEUED);

        LOG.infof("Queued order jobId=%s customer=%s item=%s quantity=%d",
                jobId, request.customerId, request.item, request.quantity);
        producerTemplate.sendBodyAndHeader("seda:orders", request, "jobId", jobId);
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
     * Marks a job as processing.
     */
    public void markProcessing(UUID jobId) {
        statuses.put(jobId, OrderStatus.PROCESSING);
        LOG.infof("Processing started jobId=%s", jobId);
    }

    /**
     * Stores a completed result and marks the job completed.
     */
    public void complete(UUID jobId, OrderResult result) {
        results.put(jobId, result);
        statuses.put(jobId, OrderStatus.COMPLETED);
        LOG.infof("Processing completed jobId=%s totalPrice=%.2f", jobId, result.totalPrice);
    }

    /**
     * Marks a job as failed.
     */
    public void fail(UUID jobId) {
        statuses.put(jobId, OrderStatus.FAILED);
        LOG.warnf("Processing failed jobId=%s", jobId);
    }

    /**
     * Creates the order result for a given request.
     */
    public OrderResult buildResult(UUID jobId, OrderRequest request) {
        OrderResult result = new OrderResult();
        result.jobId = jobId;
        result.totalPrice = request.quantity * request.unitPrice;
        result.processedAt = Instant.now();
        result.message = "Order processed for customer " + request.customerId;
        LOG.debugf("Result built jobId=%s totalPrice=%.2f", jobId, result.totalPrice);
        return result;
    }
}
