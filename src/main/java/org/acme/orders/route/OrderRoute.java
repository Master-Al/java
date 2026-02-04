package org.acme.orders.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

import org.acme.orders.model.OrderRequest;
import org.acme.orders.model.OrderResult;
import org.acme.orders.service.OrderService;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class OrderRoute extends RouteBuilder {

    @Inject
    OrderService orderService;

    /**
     * Defines the asynchronous Camel route that processes orders from the queue.
     */
    @Override
    public void configure() {
        from("seda:orders")
                .routeId("order-processor")
                .process(exchange -> {
                    UUID jobId = exchange.getIn().getHeader("jobId", UUID.class);
                    OrderRequest request = exchange.getIn().getBody(OrderRequest.class);

                    if (jobId == null || request == null) {
                        return;
                    }

                    try {
                        orderService.markProcessing(jobId);
                        OrderResult result = orderService.buildResult(jobId, request);
                        orderService.complete(jobId, result);
                    } catch (Exception e) {
                        orderService.fail(jobId);
                    }
                });
    }
}
