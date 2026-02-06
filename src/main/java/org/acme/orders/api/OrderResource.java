package org.acme.orders.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

import org.acme.orders.model.OrderRequest;
import org.acme.orders.model.OrderResult;
import org.acme.orders.model.OrderStatus;
import org.acme.orders.service.OrderService;
import org.jboss.logging.Logger;

@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject
    OrderService orderService;

    /**
     * Accepts an order and returns a job id immediately while processing happens in the background.
     */
    @POST
    public Response submit(OrderRequest request) {
        LOG.infof("Received order submission for customer=%s item=%s quantity=%d",
                request.customerId, request.item, request.quantity);
        UUID jobId = orderService.submit(request);
        LOG.infof("Accepted order jobId=%s", jobId);
        return Response.accepted(
                Map.of(
                        "jobId", jobId.toString(),
                        "status", OrderStatus.QUEUED.name()
                )
        ).build();
    }

    /**
     * Returns the current processing status for a given job id.
     */
    @GET
    @Path("/{id}/status")
    public Response status(@PathParam("id") UUID jobId) {
        OrderStatus status = orderService.getStatus(jobId);
        if (status == null) {
            LOG.warnf("Status requested for unknown jobId=%s", jobId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        LOG.infof("Status requested for jobId=%s status=%s", jobId, status);
        return Response.ok(
                Map.of(
                        "jobId", jobId.toString(),
                        "status", status.name()
                )
        ).build();
    }

    /**
     * Returns the final result if finished; otherwise returns 202 with current status.
     */
    @GET
    @Path("/{id}")
    public Response result(@PathParam("id") UUID jobId) {
        OrderResult result = orderService.getResult(jobId);
        if (result != null) {
            LOG.infof("Result returned for jobId=%s totalPrice=%.2f", jobId, result.totalPrice);
            return Response.ok(result).build();
        }

        OrderStatus status = orderService.getStatus(jobId);
        if (status == null) {
            LOG.warnf("Result requested for unknown jobId=%s", jobId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.infof("Result requested for jobId=%s but status=%s", jobId, status);
        return Response.status(Response.Status.ACCEPTED)
                .entity(
                        Map.of(
                                "jobId", jobId.toString(),
                                "status", status.name()
                        )
                )
                .build();
    }
}
