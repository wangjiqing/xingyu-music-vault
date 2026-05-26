package com.xingyu.musicvault.openapi;

import jakarta.ws.rs.core.Response;

import java.util.Map;

public class OpenApiException extends RuntimeException {
    private final Response.Status status;
    private final String code;
    private final Map<String, Object> details;

    public OpenApiException(Response.Status status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public OpenApiException(Response.Status status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public Response.Status status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
