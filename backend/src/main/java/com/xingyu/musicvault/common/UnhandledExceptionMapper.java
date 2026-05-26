package com.xingyu.musicvault.common;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        LOG.error("Unhandled API exception", exception);
        if (isOpenApiRequest()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "code", "INTERNAL_ERROR",
                            "message", "Unexpected server error",
                            "traceId", UUID.randomUUID().toString(),
                            "details", Map.of()
                    ))
                    .build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("internal_server_error", "Unexpected server error"))
                .build();
    }

    private boolean isOpenApiRequest() {
        return uriInfo != null
                && uriInfo.getPath() != null
                && uriInfo.getPath().startsWith("api/open/v1/");
    }
}
