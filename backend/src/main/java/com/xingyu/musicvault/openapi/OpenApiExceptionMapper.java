package com.xingyu.musicvault.openapi;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.UUID;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class OpenApiExceptionMapper implements ExceptionMapper<OpenApiException> {
    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(OpenApiException exception) {
        return Response.status(exception.status())
                .type(MediaType.APPLICATION_JSON)
                .entity(new OpenApiErrorResponse(
                        exception.code(),
                        exception.getMessage(),
                        traceId(),
                        exception.details()
                ))
                .build();
    }

    private String traceId() {
        if (requestContext != null) {
            Object existing = requestContext.getProperty(OpenApiSecurityFilter.TRACE_ID_PROPERTY);
            if (existing instanceof String traceId && !traceId.isBlank()) {
                return traceId;
            }
        }
        return UUID.randomUUID().toString();
    }

    public record OpenApiErrorResponse(
            String code,
            String message,
            String traceId,
            Map<String, Object> details
    ) {
    }
}
