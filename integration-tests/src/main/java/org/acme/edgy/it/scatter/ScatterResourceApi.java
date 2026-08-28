package org.acme.edgy.it.scatter;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/scatter")
public class ScatterResourceApi {

    private final AtomicInteger retryCounter = new AtomicInteger();

    @GET
    @Path("/alpha")
    public String alpha() {
        return "alpha";
    }

    @GET
    @Path("/beta")
    public String beta() {
        return "beta";
    }

    @GET
    @Path("/healthy")
    public String healthy() {
        return "healthy";
    }

    @GET
    @Path("/failing")
    public RestResponse<Void> failing() {
        return RestResponse.serverError();
    }

    @POST
    @Path("/echo")
    public String echo(String body) {
        return body;
    }

    @GET
    @Path("/retry")
    public RestResponse<String> retry() {
        if (retryCounter.incrementAndGet() <= 2) {
            return RestResponse.serverError();
        }
        return RestResponse.ok("retry-ok");
    }

    @GET
    @Path("/retry-reset")
    public RestResponse<Void> retryReset() {
        retryCounter.set(0);
        return RestResponse.ok();
    }
}
