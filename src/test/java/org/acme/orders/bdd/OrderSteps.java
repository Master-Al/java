package org.acme.orders.bdd;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.acme.orders.model.OrderRequest;
import org.acme.orders.model.OrderResult;
import org.acme.orders.model.OrderStatus;
import org.acme.orders.route.OrderRoute;
import org.acme.orders.service.OrderService;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.logging.Logger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrderSteps {

    private static final Logger LOG = Logger.getLogger(OrderSteps.class);

    private CamelContext camelContext;
    private ProducerTemplate producerTemplate;
    private OrderService orderService;
    private NotifyBuilder routeNotify;

    private OrderRequest request;
    private UUID jobId;
    private OrderStatus initialStatus;

    /**
     * Starts a local Camel context and registers the seda route for unit testing.
     */
    @Before
    public void startCamel() throws Exception {
        camelContext = new DefaultCamelContext();
        producerTemplate = camelContext.createProducerTemplate();

        orderService = new OrderService();
        orderService.setProducerTemplate(producerTemplate);

        camelContext.addRoutes(new OrderRoute(orderService));
        camelContext.start();
        producerTemplate.start();
        LOG.info("BDD Camel context started");
    }

    /**
     * Stops the Camel context after each scenario.
     */
    @After
    public void stopCamel() throws Exception {
        if (camelContext != null) {
            if (producerTemplate != null) {
                producerTemplate.stop();
            }
            camelContext.stop();
            LOG.info("BDD Camel context stopped");
        }
    }

    /**
     * Builds an order request that will be submitted in later steps.
     */
    @Given("an order request for customer {string} item {string} quantity {int} unit price {double}")
    public void buildOrderRequest(String customerId, String item, int quantity, double unitPrice) {
        request = new OrderRequest();
        request.customerId = customerId;
        request.item = item;
        request.quantity = quantity;
        request.unitPrice = unitPrice;
        LOG.infof("BDD build order customer=%s item=%s quantity=%d unitPrice=%.2f",
                customerId, item, quantity, unitPrice);
    }

    /**
     * Submits the order into the seda:orders route and stores the returned job id.
     */
    @When("the client submits the order")
    public void submitOrder() {
        routeNotify = new NotifyBuilder(camelContext)
                .fromRoute("order-processor")
                .whenCompleted(1)
                .create();

        jobId = orderService.submit(request);
        initialStatus = orderService.getStatus(jobId);

        LOG.infof("BDD submitted order jobId=%s", jobId);
        LOG.infof("BDD initial status jobId=%s status=%s", jobId, initialStatus);
    }

    /**
     * Verifies the submission was accepted and the route processed the exchange.
     */
    @Then("the submission is accepted")
    public void submissionAccepted() {
        assertNotNull(jobId);
        assertTrue(routeNotify.matches(2, TimeUnit.SECONDS),
                "Expected Camel route 'order-processor' to process the message");
        boolean accepted = OrderStatus.QUEUED.equals(initialStatus)
                || OrderStatus.PROCESSING.equals(initialStatus)
                || OrderStatus.COMPLETED.equals(initialStatus);
        assertTrue(accepted, "Expected initial status to be QUEUED/PROCESSING/COMPLETED but was " + initialStatus);
        LOG.infof("BDD submission accepted jobId=%s status=%s", jobId, initialStatus);
    }

    /**
     * Polls until the order reaches the expected status or the timeout expires.
     */
    @Then("eventually the order status is {word}")
    public void statusEventuallyEquals(String expectedStatus) {
        OrderStatus status = awaitStatus(OrderStatus.valueOf(expectedStatus));
        assertEquals(expectedStatus, status.name());
        LOG.infof("BDD status reached jobId=%s status=%s", jobId, status);
    }

    /**
     * Asserts that the completed order result contains the expected total price.
     */
    @Then("the total price is {double}")
    public void totalPriceIs(double expectedTotal) {
        OrderResult result = awaitResult();
        assertNotNull(result);
        assertEquals(jobId, result.jobId);
        assertEquals(expectedTotal, result.totalPrice, 0.001);
        LOG.infof("BDD total price validated jobId=%s totalPrice=%.2f", jobId, expectedTotal);
    }

    /**
     * Waits briefly for the order status to match the expected value.
     */
    private OrderStatus awaitStatus(OrderStatus expectedStatus) {
        long deadline = System.currentTimeMillis() + 2000;
        OrderStatus status = null;
        while (System.currentTimeMillis() < deadline) {
            status = orderService.getStatus(jobId);
            if (expectedStatus.equals(status)) {
                return status;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.warnf("BDD status timeout jobId=%s expected=%s last=%s", jobId, expectedStatus, status);
        return status;
    }

    /**
     * Waits briefly for the order result to be available.
     */
    private OrderResult awaitResult() {
        long deadline = System.currentTimeMillis() + 2000;
        OrderResult result = null;
        while (System.currentTimeMillis() < deadline) {
            result = orderService.getResult(jobId);
            if (result != null) {
                return result;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result;
    }
}
