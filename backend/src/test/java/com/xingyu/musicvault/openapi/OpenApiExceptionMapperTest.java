package com.xingyu.musicvault.openapi;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiExceptionMapperTest {
    @Test
    void reusesTraceIdFromSecurityFilterRequestContext() {
        OpenApiExceptionMapper mapper = new OpenApiExceptionMapper();
        mapper.requestContext = requestContextWithTraceId("trace-from-filter");

        Response response = mapper.toResponse(new OpenApiException(
                Response.Status.NOT_FOUND,
                "OPENAPI_TRACK_NOT_FOUND",
                "Track not found"
        ));

        OpenApiExceptionMapper.OpenApiErrorResponse error =
                (OpenApiExceptionMapper.OpenApiErrorResponse) response.getEntity();
        assertEquals("trace-from-filter", error.traceId());
    }

    private ContainerRequestContext requestContextWithTraceId(String traceId) {
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class},
                (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName())
                            && OpenApiSecurityFilter.TRACE_ID_PROPERTY.equals(args[0])) {
                        return traceId;
                    }
                    if ("getPropertyNames".equals(method.getName())) {
                        return Map.of().keySet();
                    }
                    return null;
                }
        );
    }
}
