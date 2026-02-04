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

@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService orderService;

    @POST
    public Response submit(OrderRequest request) {
        UUID jobId = orderService.submit(request);
        return Response.accepted(
                Map.of(
                        "jobId", jobId.toString(),
                        "status", OrderStatus.QUEUED.name()
                )
        ).build();
    }

    @GET
    @Path("/{id}/status")
    public Response status(@PathParam("id") UUID jobId) {
        OrderStatus status = orderService.getStatus(jobId);
        if (status == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(
                Map.of(
                        "jobId", jobId.toString(),
                        "status", status.name()
                )
        ).build();
    }

    @GET
    @Path("/{id}")
    public Response result(@PathParam("id") UUID jobId) {
        OrderResult result = orderService.getResult(jobId);
        if (result != null) {
            return Response.ok(result).build();
        }

        OrderStatus status = orderService.getStatus(jobId);
        if (status == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

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
