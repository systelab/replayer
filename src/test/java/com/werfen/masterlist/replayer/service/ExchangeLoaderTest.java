package com.werfen.masterlist.replayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.werfen.masterlist.replayer.config.ReplayerProperties;
import com.werfen.masterlist.replayer.model.CapturedExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeLoaderTest {

    private static final String VALID_JSON = """
            {
              "id": "abc",
              "capturedAt": "2026-04-16T10:00:00Z",
              "request": { "uri": "/api/test", "method": "GET", "headers": {}, "body": "" },
              "response": { "status": 200, "headers": {}, "body": "{}" }
            }
            """;

    private static final String OLDER_JSON = """
            {
              "id": "old",
              "capturedAt": "2026-04-15T08:00:00Z",
              "request": { "uri": "/api/old", "method": "GET", "headers": {}, "body": "" },
              "response": { "status": 200, "headers": {}, "body": "{}" }
            }
            """;

    @TempDir
    Path tempDir;

    private ExchangeLoader loaderFor(String directory) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ReplayerProperties props = new ReplayerProperties(
                "http://localhost:8080", directory, List.of(), 30, "auto");
        return new ExchangeLoader(mapper, props);
    }

    @Test
    void loadsValidFiles() throws IOException {
        Files.writeString(tempDir.resolve("exchange1.json"), VALID_JSON);
        List<CapturedExchange> result = loaderFor(tempDir.toString()).loadAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("abc");
    }

    @Test
    void sortsByCapturedAtAscending() throws IOException {
        Files.writeString(tempDir.resolve("newer.json"), VALID_JSON);  // 2026-04-16
        Files.writeString(tempDir.resolve("older.json"), OLDER_JSON);  // 2026-04-15
        List<CapturedExchange> result = loaderFor(tempDir.toString()).loadAll();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("old");
        assertThat(result.get(1).id()).isEqualTo("abc");
    }

    @Test
    void skipsMalformedFiles() throws IOException {
        Files.writeString(tempDir.resolve("good.json"), VALID_JSON);
        Files.writeString(tempDir.resolve("bad.json"), "not valid json {{{");
        List<CapturedExchange> result = loaderFor(tempDir.toString()).loadAll();
        assertThat(result).hasSize(1);
    }

    @Test
    void handlesEmptyDirectory() throws IOException {
        List<CapturedExchange> result = loaderFor(tempDir.toString()).loadAll();
        assertThat(result).isEmpty();
    }

    @Test
    void throwsForNonexistentDirectory() {
        assertThatThrownBy(() -> loaderFor("/does/not/exist").loadAll())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void ignoresNonJsonFiles() throws IOException {
        Files.writeString(tempDir.resolve("exchange.json"), VALID_JSON);
        Files.writeString(tempDir.resolve("notes.txt"), "ignore me");
        List<CapturedExchange> result = loaderFor(tempDir.toString()).loadAll();
        assertThat(result).hasSize(1);
    }
}
