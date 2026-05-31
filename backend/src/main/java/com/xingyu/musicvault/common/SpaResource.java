package com.xingyu.musicvault.common;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.io.InputStream;

/**
 * Catch-all resource that serves {@code index.html} for client-side SPA routes.
 *
 * <p>In JAX-RS, the "most literal characters" matching rule ensures that
 * concrete routes like {@code /api/health} and {@code /api/open/v1/server/info}
 * always take precedence over the regex catch-all {@code {path:.*}}.</p>
 *
 * <p>Static assets ({@code /assets/*.js}, {@code /icons.svg}, etc.) are served
 * by the Vert.x static resource handler before RESTEasy sees them.</p>
 */
@Path("")
public class SpaResource {

    @GET
    @Path("{path:.*}")
    public Response serveSpa(@Context UriInfo uriInfo) {
        String path = uriInfo.getPath();
        // Never intercept API routes or Quarkus dev UI paths.
        if (path.startsWith("api/") || path.startsWith("q/")) {
            return Response.status(404).build();
        }
        InputStream html = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/index.html");
        if (html == null) {
            return Response.status(404).build();
        }
        return Response.ok(html)
                .type(MediaType.TEXT_HTML)
                .header("Cache-Control", "no-cache")
                .build();
    }
}
