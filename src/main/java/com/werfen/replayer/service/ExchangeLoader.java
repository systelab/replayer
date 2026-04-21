package com.werfen.replayer.service;

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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class ExchangeLoader {

    private static final Logger log = LoggerFactory.getLogger(ExchangeLoader.class);
    private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final ObjectMapper objectMapper;
    private final ReplayerProperties properties;

    public ExchangeLoader(ObjectMapper objectMapper, ReplayerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record ExchangeWithPath(CapturedExchange exchange, Path sourcePath) {}

    public Stream<ExchangeWithPath> stream() throws IOException {
        Path dir = Path.of(properties.exchangesDirectory());
        if (!Files.isDirectory(dir)) {
            throw new IOException("Exchanges directory not found or not a directory: " + dir.toAbsolutePath());
        }

        List<Path> sorted;
        try (var walk = Files.walk(dir)) {
            sorted = walk
                    .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::timestampFromFilename))
                    .toList();
        }

        return sorted.stream()
                .map(p -> {
                    CapturedExchange exchange = parseQuietly(p);
                    return exchange != null ? new ExchangeWithPath(exchange, p) : null;
                })
                .filter(Objects::nonNull);
    }

    private Instant timestampFromFilename(Path file) {
        String name = file.getFileName().toString();
        if (name.length() >= 19) {
            try {
                return LocalDateTime.parse(name.substring(0, 19), FILENAME_TS).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {}
        }
        return Instant.EPOCH;
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
