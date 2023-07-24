package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.auto.service.AutoService;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

@Slf4j
@AutoService(TriggerProcessor.class)
public class PrometheusTriggerProcessor implements TriggerProcessor {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
                                                         .build();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5L);
    private final HttpClient httpClient = HttpClient.newBuilder()
                                                    .version(HttpClient.Version.HTTP_1_1)
                                                    .connectTimeout(CONNECT_TIMEOUT)
                                                    .build();

    @Override
    public String getType() {
        return "prometheus";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, TriggerDefinition trigger, int replicaCount) {
        /*
          metadata:
            serverAddress:
            query:
            type: [Average/Max]
            threshold:
         */
        var serverAddress = requireNonNull(trigger.getMetadata().get("serverAddress"));
        var query = requireNonNull(trigger.getMetadata().get("query"));
        var type = requireNonNull(trigger.getMetadata().get("type"));
        var threshold = Long.parseLong(requireNonNull(trigger.getMetadata().get("threshold")));
        var uri = buildMetricsUri(serverAddress, query,autoscaler.getSpec().getScaleTargetRef().getName());
        var request = HttpRequest.newBuilder()
                                 .uri(uri)
                                 .build();
        logger.debug("Requesting metrics from the prometheus API: {}", uri);

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var json = MAPPER.readValue(response.body(), QueryResponse.class);

            var value = Long.parseLong((String) json.data().result()[0].value()[1]);

            if (type.equals("Total")) {
                value /= replicaCount;
            }

            return new TriggerResult(trigger, value, threshold);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private URI buildMetricsUri(String prometheusAddress, String prometheusQuery, String resourceName) {
        var formattedQuery = prometheusQuery.replace("{{resource_name}}", resourceName);

        return URI.create(String.format("%s/api/v1/query?query=%s", prometheusAddress, urlEncode(formattedQuery)));
    }

    private static String urlEncode(String formattedQuery) {
        return URLEncoder.encode(formattedQuery, StandardCharsets.UTF_8);
    }

    private record QueryResponse(String status, QueryData data) {}

    private record QueryData(String resultType, QueryDataResult[] result) {}

    private record QueryDataResult(Map<String, String> metric, Object[] value) {}
}
