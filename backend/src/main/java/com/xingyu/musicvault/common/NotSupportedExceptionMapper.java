package com.xingyu.musicvault.common;

import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class NotSupportedExceptionMapper implements ExceptionMapper<NotSupportedException> {
    @Override
    public Response toResponse(NotSupportedException exception) {
        return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                .entity(new ErrorResponse("unsupported_media_type", "Unsupported content type"))
                .build();
    }
}
