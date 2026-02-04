package org.acme.orders.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import org.acme.orders.model.OrderRequest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class OrderResourceTest {

    /**
     * Submits an order and verifies it is processed asynchronously end-to-end.
     */
    @Test
    public void submitAndProcessOrder() {
        OrderRequest request = new OrderRequest();
        request.customerId = "cust-1";
        request.item = "widget";
        request.quantity = 2;
        request.unitPrice = 10.0;

        String jobId = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(202)
                .body("status", equalTo("QUEUED"))
                .extract()
                .path("jobId");

        String status = awaitCompleted(jobId);
        assertEquals("COMPLETED", status);

        given()
                .when()
                .get("/orders/{id}", jobId)
                .then()
                .statusCode(200)
                .body("jobId", equalTo(jobId))
                .body("totalPrice", equalTo(20.0f));
    }

    /**
     * Polls the status endpoint until the job completes or a short timeout elapses.
     */
    private String awaitCompleted(String jobId) {
        long deadline = System.currentTimeMillis() + 2000;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = given()
                    .when()
                    .get("/orders/{id}/status", jobId)
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("status");
            if ("COMPLETED".equals(status)) {
                return status;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return status;
    }
}
