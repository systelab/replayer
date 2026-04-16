package com.werfen.replayer.service;

import com.werfen.replayer.config.ReplayerProperties;
import com.werfen.replayer.model.CapturedRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
public class RequestReplayer {

    private final WebClient webClient;
    private final ReplayerProperties properties;

    public RequestReplayer(WebClient webClient, ReplayerProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public record ReplayedResponse(int statusCode, String body) {}

    /**
     * Sends the captured request to the target base URL and returns the actual response.
     * 4xx/5xx responses are returned as-is rather than thrown as exceptions,
     * so the replayer can compare error responses against the captured expectation.
     */
    public ReplayedResponse replay(CapturedRequest request) {
        String fullUri = properties.targetBaseUrl() + request.uri();
        Duration timeout = Duration.ofSeconds(properties.requestTimeoutSeconds());

        var requestSpec = webClient
                .method(HttpMethod.valueOf(request.method()))
                .uri(fullUri);

        if (request.headers() != null) {
            request.headers().forEach(requestSpec::header);
        }

        var bodySpec = (request.body() != null && !request.body().isBlank())
                ? requestSpec.bodyValue(request.body())
                : requestSpec;

        return bodySpec
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new ReplayedResponse(response.statusCode().value(), body)))
                .block(timeout);
    }
}
