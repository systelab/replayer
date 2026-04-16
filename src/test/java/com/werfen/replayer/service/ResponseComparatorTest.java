package com.werfen.replayer.service;

import com.werfen.replayer.comparison.ComparisonResult;
import com.werfen.replayer.comparison.JsonComparator;
import com.werfen.replayer.comparison.XmlComparator;
import com.werfen.replayer.config.ReplayerProperties;
import com.werfen.replayer.model.CapturedExchange;
import com.werfen.replayer.model.CapturedRequest;
import com.werfen.replayer.model.CapturedResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseComparatorTest {

    private final JsonComparator jsonCmp = new JsonComparator();
    private final XmlComparator  xmlCmp  = new XmlComparator();

    private ResponseComparator comparatorWith(String contentTypeOverride,
                                              List<String> ignoreFields) {
        ReplayerProperties props = new ReplayerProperties(
                "http://localhost", "./exchanges", ignoreFields, 30, contentTypeOverride);
        return new ResponseComparator(jsonCmp, xmlCmp, props);
    }

    private CapturedExchange exchange(int status, String contentType, String body) {
        Map<String, String> headers = contentType != null
                ? Map.of("Content-Type", contentType)
                : Map.of();
        CapturedRequest req = new CapturedRequest("/api/test", "GET", Map.of(), null);
        CapturedResponse res = new CapturedResponse(status, headers, body);
        return new CapturedExchange("id", Instant.now(), req, res);
    }

    private RequestReplayer.ReplayedResponse actual(int status, String body) {
        return new RequestReplayer.ReplayedResponse(status, body);
    }

    // -----------------------------------------------------------------------
    // Status comparison
    // -----------------------------------------------------------------------

    @Test
    void statusMismatchFails() {
        ComparisonResult result = comparatorWith("auto", List.of())
                .compare(exchange(200, "application/json", "{}"),
                         actual(404, "{}"));
        assertThat(result.passed()).isFalse();
        assertThat(result.diffs()).anyMatch(d -> d.contains("Status mismatch"));
    }

    @Test
    void statusMatchWithBodyMatchPasses() {
        ComparisonResult result = comparatorWith("auto", List.of())
                .compare(exchange(200, "application/json", "{\"x\":1}"),
                         actual(200, "{\"x\":1}"));
        assertThat(result.passed()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Body comparison routing
    // -----------------------------------------------------------------------

    @Test
    void jsonBodyDiffFails() {
        ComparisonResult result = comparatorWith("auto", List.of())
                .compare(exchange(200, "application/json", "{\"a\":1}"),
                         actual(200, "{\"a\":2}"));
        assertThat(result.passed()).isFalse();
    }

    @Test
    void jsonContentTypeHeaderRoutes() {
        // Different body but ignore the differing field — passes only if JSON comparator was used
        ComparisonResult result = comparatorWith("auto", List.of("id"))
                .compare(exchange(200, "application/json", "{\"id\":\"old\",\"name\":\"x\"}"),
                         actual(200, "{\"id\":\"new\",\"name\":\"x\"}"));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void xmlContentTypeHeaderRoutes() {
        ComparisonResult result = comparatorWith("auto", List.of())
                .compare(exchange(200, "application/xml",
                                  "<r><a>1</a></r>"),
                         actual(200, "<r><a>1</a></r>"));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void bodySniffingDetectsJson() {
        // No Content-Type header, body starts with {
        ComparisonResult result = comparatorWith("auto", List.of())
                .compare(exchange(200, null, "{\"a\":1}"),
                         actual(200, "{\"a\":1}"));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void bodySniffingDetectsXml() {
        ComparisonResult result = comparatorWith("auto", List.of())
                .compare(exchange(200, null, "<root><val>1</val></root>"),
                         actual(200, "<root><val>1</val></root>"));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void contentTypeOverrideForceJson() {
        // Force JSON even though no header
        ComparisonResult result = comparatorWith("json", List.of("id"))
                .compare(exchange(200, null, "{\"id\":\"a\",\"name\":\"x\"}"),
                         actual(200, "{\"id\":\"b\",\"name\":\"x\"}"));
        assertThat(result.passed()).isTrue();
    }
}
