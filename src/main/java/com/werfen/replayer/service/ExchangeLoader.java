package com.werfen.replayer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werfen.replayer.config.ReplayerProperties;
import com.werfen.replayer.model.CapturedExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class ExchangeLoader {

    private static final Logger log = LoggerFactory.getLogger(ExchangeLoader.class);

    private final ObjectMapper objectMapper;
    private final ReplayerProperties properties;

    public ExchangeLoader(ObjectMapper objectMapper, ReplayerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Stream<CapturedExchange> stream() throws IOException {
        Path dir = Path.of(properties.exchangesDirectory());
        if (!Files.isDirectory(dir)) {
            throw new IOException("Exchanges directory not found or not a directory: " + dir.toAbsolutePath());
        }

        List<PathWithTimestamp> sorted;
        try (var ls = Files.list(dir)) {
            sorted = ls
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(this::readTimestamp)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(PathWithTimestamp::capturedAt))
                    .toList();
        }

        return sorted.stream()
                .map(pwt -> parseQuietly(pwt.path()))
                .filter(Objects::nonNull);
    }

    private record PathWithTimestamp(Path path, Instant capturedAt) {}

    private PathWithTimestamp readTimestamp(Path file) {
        try {
            JsonNode node = objectMapper.readTree(file.toFile());
            Instant ts = node.has("capturedAt") && !node.get("capturedAt").isNull()
                    ? Instant.parse(node.get("capturedAt").asText())
                    : Instant.EPOCH;
            return new PathWithTimestamp(file, ts);
        } catch (IOException e) {
            log.warn("Skipping malformed exchange file {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    private CapturedExchange parseQuietly(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), CapturedExchange.class);
        } catch (IOException e) {
            log.warn("Skipping malformed exchange file {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }
}
