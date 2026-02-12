package org.acme.orders.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;

import org.acme.orders.model.OrderRequest;
import org.acme.orders.model.OrderResult;
import org.acme.orders.model.OrderStatus;
import org.acme.orders.service.OrderService;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderRestRoute extends RouteBuilder {

    private static final Logger LOG = Logger.getLogger(OrderRestRoute.class);

    @Inject
    OrderService orderService;

    /**
     * Exposes REST endpoints for submitting orders and reading status/results.
     */
    @Override
    public void configure() {
        restConfiguration()
                .component("platform-http")
                .bindingMode(RestBindingMode.json);

        rest("/orders")
                .post()
                .type(OrderRequest.class)
                .to("direct:orders-submit")
                .get("/{id}/status")
                .to("direct:orders-status")
                .get("/{id}")
                .to("direct:orders-result");

        from("direct:orders-submit")
                .process(exchange -> {
                    OrderRequest request = exchange.getIn().getBody(OrderRequest.class);
                    UUID jobId = orderService.submit(request);
                    LOG.infof("REST accepted jobId=%s", jobId);
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
                    exchange.getMessage().setBody(Map.of(
                            "jobId", jobId.toString(),
                            "status", OrderStatus.QUEUED.name()
                    ));
                });

        from("direct:orders-status")
                .process(exchange -> {
                    UUID jobId = parseJobId(exchange);
                    if (jobId == null) {
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                        exchange.getMessage().setBody(Map.of("message", "Invalid job id"));
                        return;
                    }
                    OrderStatus status = orderService.getStatus(jobId);
                    if (status == null) {
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        return;
                    }
                    exchange.getMessage().setBody(Map.of(
                            "jobId", jobId.toString(),
                            "status", status.name()
                    ));
                });

        from("direct:orders-result")
                .process(exchange -> {
                    UUID jobId = parseJobId(exchange);
                    if (jobId == null) {
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                        exchange.getMessage().setBody(Map.of("message", "Invalid job id"));
                        return;
                    }
                    OrderResult result = orderService.getResult(jobId);
                    if (result != null) {
                        exchange.getMessage().setBody(result);
                        return;
                    }
                    OrderStatus status = orderService.getStatus(jobId);
                    if (status == null) {
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        return;
                    }
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
                    exchange.getMessage().setBody(Map.of(
                            "jobId", jobId.toString(),
                            "status", status.name()
                    ));
                });
    }

    private UUID parseJobId(Exchange exchange) {
        String raw = exchange.getIn().getHeader("id", String.class);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
