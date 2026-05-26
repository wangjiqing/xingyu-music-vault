package com.xingyu.musicvault.openapi;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.UUID;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class OpenApiExceptionMapper implements ExceptionMapper<OpenApiException> {
    @Override
    public Response toResponse(OpenApiException exception) {
        return Response.status(exception.status())
                .type(MediaType.APPLICATION_JSON)
                .entity(new OpenApiErrorResponse(
                        exception.code(),
                        exception.getMessage(),
                        UUID.randomUUID().toString(),
                        exception.details()
                ))
                .build();
    }

    public record OpenApiErrorResponse(
            String code,
            String message,
            String traceId,
            Map<String, Object> details
    ) {
    }
}
