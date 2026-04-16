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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ExchangeLoader {

    private static final Logger log = LoggerFactory.getLogger(ExchangeLoader.class);

    private final ObjectMapper objectMapper;
    private final ReplayerProperties properties;

    public ExchangeLoader(ObjectMapper objectMapper, ReplayerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Loads all {@code .json} files from the configured exchanges directory,
     * sorted ascending by {@code capturedAt} to preserve replay order.
     * Malformed files are logged and skipped.
     */
    public List<CapturedExchange> loadAll() throws IOException {
        Path dir = Path.of(properties.exchangesDirectory());

        if (!Files.isDirectory(dir)) {
            throw new IOException("Exchanges directory not found or not a directory: " + dir.toAbsolutePath());
        }

        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(this::parseQuietly)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(e -> e.capturedAt() != null ? e.capturedAt()
                            : java.time.Instant.EPOCH))
                    .toList();
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
