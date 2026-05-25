package com.xingyu.musicvault.metadata;

import com.xingyu.musicvault.common.ErrorResponse;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class AudioMetadataExceptionMapper implements ExceptionMapper<AudioMetadataException> {
    @Override
    public Response toResponse(AudioMetadataException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse("conflict", exception.getMessage()))
                .build();
    }
}
