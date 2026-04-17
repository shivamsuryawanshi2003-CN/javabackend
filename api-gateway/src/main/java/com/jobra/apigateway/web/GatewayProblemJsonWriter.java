package com.jobra.apigateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 7807-style problem responses with a stable JSON shape for API clients.
 */
@Component
public class GatewayProblemJsonWriter {

    private final ObjectMapper objectMapper;
    private final GatewayCorsHeaders corsHeaders;

    public GatewayProblemJsonWriter(ObjectMapper objectMapper, GatewayCorsHeaders corsHeaders) {
        this.objectMapper = objectMapper;
        this.corsHeaders = corsHeaders;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String title, String detail, String code) {
        ServerHttpResponse response = exchange.getResponse();
        corsHeaders.apply(exchange, response);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.parseMediaType("application/problem+json"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"title\":\"" + title + "\",\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        }
        DataBuffer buf = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buf));
    }
}
