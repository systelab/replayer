package com.werfen.replayer.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.werfen.replayer.comparison.ComparisonResult;
import com.werfen.replayer.comparison.JsonComparator;
import com.werfen.replayer.comparison.XmlComparator;
import com.werfen.replayer.config.ReplayerProperties;
import com.werfen.replayer.model.CapturedExchange;
import com.werfen.replayer.report.ReportPrinter;
import com.werfen.replayer.service.ExchangeLoader;
import com.werfen.replayer.service.RequestReplayer;
import com.werfen.replayer.service.ResponseComparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test wiring the full replay pipeline against a WireMock server.
 * System.exit() is NOT called — results are returned for assertion.
 */
class ReplayRunnerIntegrationTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    private List<ComparisonResult> runReplay(Path dir, List<String> ignoreFields) throws Exception {
        ReplayerProperties props = new ReplayerProperties(
                "http://localhost:" + wireMock.port(),
                dir.toString(),
                ignoreFields,
                10,
                "auto");

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ExchangeLoader loader      = new ExchangeLoader(mapper, props);
        RequestReplayer replayer   = new RequestReplayer(WebClient.builder().build(), props);
        ResponseComparator cmp     = new ResponseComparator(
                new JsonComparator(), new XmlComparator(), props);
        ReportPrinter printer      = new ReportPrinter();

        List<CapturedExchange> exchanges = loader.loadAll();
        List<ComparisonResult> results   = new ArrayList<>();
        for (CapturedExchange exchange : exchanges) {
            results.add(cmp.compare(exchange, replayer.replay(exchange.request())));
        }
        printer.printSummary(results);
        return results;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void allPassesWhenResponsesMatch(@TempDir Path dir) throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/orders"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"orders\":[]}")));

        Files.writeString(dir.resolve("exchange1.json"), """
                {
                  "id": "id-1",
                  "capturedAt": "2026-04-16T10:00:00Z",
                  "request": { "uri": "/api/orders", "method": "GET",
                                "headers": { "Accept": "application/json" }, "body": "" },
                  "response": { "status": 200,
                                "headers": { "Content-Type": "application/json" },
                                "body": "{\\"orders\\":[]}" }
                }
                """);

        assertThat(runReplay(dir, List.of())).allMatch(ComparisonResult::passed);
    }

    @Test
    void oneFailureIsReported(@TempDir Path dir) throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/items"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"count\":5}")));

        Files.writeString(dir.resolve("exchange1.json"), """
                {
                  "id": "id-2",
                  "capturedAt": "2026-04-16T10:01:00Z",
                  "request": { "uri": "/api/items", "method": "GET",
                                "headers": {}, "body": "" },
                  "response": { "status": 200,
                                "headers": { "Content-Type": "application/json" },
                                "body": "{\\"count\\":99}" }
                }
                """);

        List<ComparisonResult> results = runReplay(dir, List.of());
        assertThat(results.stream().filter(r -> !r.passed()).count()).isEqualTo(1);
    }

    @Test
    void statusMismatchCaptured(@TempDir Path dir) throws Exception {
        wireMock.stubFor(get(urlEqualTo("/api/broken"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"boom\"}")));

        Files.writeString(dir.resolve("exchange1.json"), """
                {
                  "id": "id-3",
                  "capturedAt": "2026-04-16T10:02:00Z",
                  "request": { "uri": "/api/broken", "method": "GET",
                                "headers": {}, "body": "" },
                  "response": { "status": 200,
                                "headers": { "Content-Type": "application/json" },
                                "body": "{\\"ok\\":true}" }
                }
                """);

        List<ComparisonResult> results = runReplay(dir, List.of());
        assertThat(results).hasSize(1);
        ComparisonResult r = results.get(0);
        assertThat(r.passed()).isFalse();
        assertThat(r.expectedStatus()).isEqualTo(200);
        assertThat(r.actualStatus()).isEqualTo(500);
    }

    @Test
    void runOrderMatchesCapturedAt(@TempDir Path dir) throws Exception {
        wireMock.stubFor(get(anyUrl())
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        Files.writeString(dir.resolve("b_exchange.json"), """
                {
                  "id": "second",
                  "capturedAt": "2026-04-16T10:02:00Z",
                  "request": { "uri": "/api/b", "method": "GET", "headers": {}, "body": "" },
                  "response": { "status": 200, "headers": {}, "body": "{}" }
                }
                """);
        Files.writeString(dir.resolve("a_exchange.json"), """
                {
                  "id": "first",
                  "capturedAt": "2026-04-16T10:01:00Z",
                  "request": { "uri": "/api/a", "method": "GET", "headers": {}, "body": "" },
                  "response": { "status": 200, "headers": {}, "body": "{}" }
                }
                """);

        ReplayerProperties props = new ReplayerProperties(
                "http://localhost:" + wireMock.port(), dir.toString(), List.of(), 10, "auto");
        ExchangeLoader loader = new ExchangeLoader(
                new ObjectMapper().registerModule(new JavaTimeModule()), props);

        List<CapturedExchange> exchanges = loader.loadAll();
        assertThat(exchanges).hasSize(2);
        assertThat(exchanges.get(0).id()).isEqualTo("first");
        assertThat(exchanges.get(1).id()).isEqualTo("second");
    }
}
