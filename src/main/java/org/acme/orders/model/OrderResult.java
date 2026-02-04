package org.acme.orders.model;

import java.time.Instant;
import java.util.UUID;

public class OrderResult {
    public UUID jobId;
    public double totalPrice;
    public Instant processedAt;
    public String message;
}
