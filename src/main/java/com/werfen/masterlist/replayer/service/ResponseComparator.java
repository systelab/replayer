package com.werfen.masterlist.replayer.service;

import com.werfen.masterlist.replayer.comparison.ComparisonResult;
import com.werfen.masterlist.replayer.comparison.JsonComparator;
import com.werfen.masterlist.replayer.comparison.XmlComparator;
import com.werfen.masterlist.replayer.config.ReplayerProperties;
import com.werfen.masterlist.replayer.model.CapturedExchange;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ResponseComparator {

    private enum ContentType { JSON, XML, UNKNOWN }

    private final JsonComparator jsonComparator;
    private final XmlComparator xmlComparator;
    private final ReplayerProperties properties;

    public ResponseComparator(JsonComparator jsonComparator,
                              XmlComparator xmlComparator,
                              ReplayerProperties properties) {
        this.jsonComparator = jsonComparator;
        this.xmlComparator = xmlComparator;
        this.properties = properties;
    }

    public ComparisonResult compare(CapturedExchange exchange,
                                    RequestReplayer.ReplayedResponse actual) {
        String url = exchange.request().uri();
        String method = exchange.request().method();
        int expectedStatus = exchange.response().status();
        int actualStatus = actual.statusCode();

        List<String> diffs = new ArrayList<>();

        if (expectedStatus != actualStatus) {
            diffs.add("Status mismatch: expected=" + expectedStatus + " actual=" + actualStatus);
        }

        String expectedBody = exchange.response().body();
        String actualBody = actual.body();
        ContentType type = detectContentType(exchange, actual);

        List<String> bodyDiffs = switch (type) {
            case JSON -> jsonComparator.compare(expectedBody, actualBody, properties.ignoreFields());
            case XML  -> xmlComparator.compare(expectedBody, actualBody);
            case UNKNOWN -> compareRaw(expectedBody, actualBody);
        };

        diffs.addAll(bodyDiffs);

        if (diffs.isEmpty()) {
            return ComparisonResult.pass(url, method, actualStatus);
        }
        return ComparisonResult.fail(url, method, expectedStatus, actualStatus, diffs);
    }

    private ContentType detectContentType(CapturedExchange exchange,
                                          RequestReplayer.ReplayedResponse actual) {
        String override = properties.contentTypeDetection();
        if (override != null && !override.equalsIgnoreCase("auto")) {
            return override.equalsIgnoreCase("xml") ? ContentType.XML : ContentType.JSON;
        }

        // Check Content-Type header of the captured response first
        Map<String, String> headers = exchange.response().headers();
        if (headers != null) {
            String ct = headers.getOrDefault("Content-Type",
                    headers.getOrDefault("content-type", ""));
            if (ct.contains("json")) return ContentType.JSON;
            if (ct.contains("xml"))  return ContentType.XML;
        }

        // Fall back to body sniffing
        String body = exchange.response().body();
        if (body != null) {
            String trimmed = body.stripLeading();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return ContentType.JSON;
            if (trimmed.startsWith("<")) return ContentType.XML;
        }

        return ContentType.UNKNOWN;
    }

    private List<String> compareRaw(String expected, String actual) {
        if (expected == null && actual == null) return List.of();
        if (expected == null || actual == null) {
            return List.of("Body mismatch: expected=" + expected + " actual=" + actual);
        }
        if (!expected.equals(actual)) {
            return List.of("Body mismatch (raw): bodies differ");
        }
        return List.of();
    }
}
