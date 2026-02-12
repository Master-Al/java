package org.acme.orders.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

import org.acme.orders.model.OrderRequest;
import org.acme.orders.model.OrderResult;
import org.acme.orders.service.OrderService;
import org.apache.camel.builder.RouteBuilder;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderRoute extends RouteBuilder {

    private static final Logger LOG = Logger.getLogger(OrderRoute.class);

    private final OrderService orderService;

    @Inject
    public OrderRoute(OrderService orderService) {
        this.orderService = orderService;
    }

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
                        LOG.warn("Skipping order message missing jobId or request body");
                        return;
                    }

                    try {
                        LOG.infof("Route received jobId=%s", jobId);
                        orderService.markProcessing(jobId);
                        OrderResult result = orderService.buildResult(jobId, request);
                        orderService.complete(jobId, result);
                    } catch (Exception e) {
                        LOG.errorf(e, "Route failed jobId=%s", jobId);
                        orderService.fail(jobId);
                    }
                });
    }
}
