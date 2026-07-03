package com.werfen.replayer.service;

import com.werfen.replayer.config.ReplayerProperties;
import com.werfen.replayer.model.CapturedRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Set;

@Service
public class RequestReplayer {

    private final WebClient webClient;
    private final ReplayerProperties properties;

    public RequestReplayer(WebClient webClient, ReplayerProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    /** Response header emitted by the recorder's filter carrying the server-internal time (ms). */
    private static final String DURATION_HEADER = "X-Replay-Duration-Millis";

    /**
     * @param durationMillis       client-observed round-trip time (always measured)
     * @param serverDurationMillis server-internal time reported by the target via
     *                             {@code X-Replay-Duration-Millis}, or null if the target
     *                             did not send the header
     */
    public record ReplayedResponse(int statusCode, String body,
                                   long durationMillis, Long serverDurationMillis) {}

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

        // Strip transport-level / hop-by-hop headers that must not be forwarded
        Set<String> skipHeaders = Set.of(
            "accept-encoding", "content-length", "host",
            "transfer-encoding", "connection");
        if (request.headers() != null) {
            request.headers().forEach((name, value) -> {
                if (!skipHeaders.contains(name.toLowerCase())) {
                    requestSpec.header(name, value);
                }
            });
        }

        var bodySpec = (request.body() != null && !request.body().isBlank())
            ? requestSpec.bodyValue(request.body())
            : requestSpec;

        long startNanos = System.nanoTime();
        ReplayedResponse received = bodySpec
            .exchangeToMono(response -> {
                Long serverMillis = parseServerDuration(
                    response.headers().asHttpHeaders().getFirst(DURATION_HEADER));
                return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> new ReplayedResponse(
                        response.statusCode().value(), body, 0L, serverMillis));
            })
            .block(timeout);
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;

        return new ReplayedResponse(received.statusCode(), received.body(),
            durationMillis, received.serverDurationMillis());
    }

    private static Long parseServerDuration(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(headerValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
