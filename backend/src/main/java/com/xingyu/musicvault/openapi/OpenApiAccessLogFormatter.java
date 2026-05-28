package com.xingyu.musicvault.openapi;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OpenApiAccessLogFormatter {
    public String format(String method, String path, int status, long durationMs, String clientIp, String traceId) {
        return "OpenAPI access method=" + safe(method)
                + " path=/" + safe(trimLeadingSlash(path))
                + " status=" + status
                + " durationMs=" + durationMs
                + " clientIp=" + safe(clientIp)
                + " traceId=" + safe(traceId);
    }

    private String trimLeadingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\x00-\\x1F\\x7F]", "_");
    }
}
