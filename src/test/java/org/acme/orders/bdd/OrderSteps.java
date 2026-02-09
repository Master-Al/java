package org.acme.orders.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.acme.orders.model.OrderRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.jboss.logging.Logger;
import jakarta.inject.Inject;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrderSteps {

    private static final Logger LOG = Logger.getLogger(OrderSteps.class);

    @Inject
    CamelContext camelContext;

    private OrderRequest request;
    private String jobId;
    private String initialStatus;
    private NotifyBuilder routeNotify;

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
     * Submits the order to the REST endpoint and stores the returned job id.
     */
    @When("the client submits the order")
    public void submitOrder() {
        routeNotify = new NotifyBuilder(camelContext)
                .fromRoute("order-processor")
                .whenCompleted(1)
                .create();

        Response response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .extract()
                .response();
        assertEquals(202, response.statusCode());
        jobId = response.path("jobId");

        LOG.infof("BDD submitted order jobId=%s", jobId);
        Response statusResponse = given()
                .when()
                .get("/orders/{id}/status", jobId)
                .then()
                .extract()
                .response();
        assertEquals(200, statusResponse.statusCode());
        initialStatus = statusResponse.path("status");
        LOG.infof("BDD initial status jobId=%s status=%s", jobId, initialStatus);
    }

    /**
     * Verifies the submission was accepted and a job id was generated.
     */
    @Then("the submission is accepted")
    public void submissionAccepted() {
        assertNotNull(jobId);
        assertTrue(routeNotify.matches(2, java.util.concurrent.TimeUnit.SECONDS),
                "Expected Camel route 'order-processor' to process the message");
        boolean accepted = "QUEUED".equals(initialStatus) || "PROCESSING".equals(initialStatus) || "COMPLETED".equals(initialStatus);
        assertTrue(accepted, "Expected initial status to be QUEUED/PROCESSING/COMPLETED but was " + initialStatus);
        LOG.infof("BDD submission accepted jobId=%s status=%s", jobId, initialStatus);
    }

    /**
     * Polls until the order reaches the expected status or the timeout expires.
     */
    @Then("eventually the order status is {word}")
    public void statusEventuallyEquals(String expectedStatus) {
        String status = awaitStatus(expectedStatus);
        assertEquals(expectedStatus, status);
        LOG.infof("BDD status reached jobId=%s status=%s", jobId, status);
    }

    /**
     * Asserts that the completed order result contains the expected total price.
     */
    @Then("the total price is {double}")
    public void totalPriceIs(double expectedTotal) {
        Response resultResponse = given()
                .when()
                .get("/orders/{id}", jobId)
                .then()
                .extract()
                .response();
        assertEquals(200, resultResponse.statusCode());
        assertEquals(jobId, resultResponse.path("jobId"));
        Float totalPrice = resultResponse.path("totalPrice");
        assertNotNull(totalPrice);
        assertEquals((float) expectedTotal, totalPrice);
        LOG.infof("BDD total price validated jobId=%s totalPrice=%.2f", jobId, expectedTotal);
    }

    /**
     * Waits briefly for the order status to match the expected value.
     */
    private String awaitStatus(String expectedStatus) {
        long deadline = System.currentTimeMillis() + 2000;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            Response statusResponse = given()
                    .when()
                    .get("/orders/{id}/status", jobId)
                    .then()
                    .extract()
                    .response();
            assertEquals(200, statusResponse.statusCode());
            status = statusResponse.path("status");
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
}
