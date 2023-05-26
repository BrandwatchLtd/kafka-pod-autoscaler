package com.brandwatch.kafka_pod_autoscaler.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.micrometer.prometheus.PrometheusMeterRegistry;

public class MetricsHandler implements HttpHandler {
    private final PrometheusMeterRegistry registry;

    public MetricsHandler(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        try (var outputStream = httpExchange.getResponseBody()) {
            var message = registry.scrape();
            var bytes = message.getBytes(StandardCharsets.UTF_8);
            httpExchange.sendResponseHeaders(200, bytes.length);
            outputStream.write(bytes);
            outputStream.flush();
        }
    }
}
