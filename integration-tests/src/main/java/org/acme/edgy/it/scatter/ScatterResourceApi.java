package org.acme.edgy.it.scatter;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/api/scatter")
public class ScatterResourceApi {

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
}
