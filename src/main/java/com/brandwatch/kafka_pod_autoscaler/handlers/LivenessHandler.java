package com.brandwatch.kafka_pod_autoscaler.handlers;

import static com.brandwatch.kafka_pod_autoscaler.handlers.StartupHandler.sendMessage;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.javaoperatorsdk.operator.Operator;

public class LivenessHandler implements HttpHandler {

    private final Operator operator;

    public LivenessHandler(Operator operator) {
        this.operator = operator;
    }

    // custom logic can be added here based on the health of event sources
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (operator.getRuntimeInfo().allEventSourcesAreHealthy()) {
            sendMessage(httpExchange, 200, "healthy");
        } else {
            sendMessage(httpExchange, 400, "an event source is not healthy");
        }
    }
}
