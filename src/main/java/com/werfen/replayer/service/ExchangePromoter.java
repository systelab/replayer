package com.werfen.replayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werfen.replayer.model.CapturedExchange;
import com.werfen.replayer.model.CapturedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class ExchangePromoter {

    private static final Logger log = LoggerFactory.getLogger(ExchangePromoter.class);

    private final ObjectMapper objectMapper;

    public ExchangePromoter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void promote(ExchangeLoader.ExchangeWithPath ewp, RequestReplayer.ReplayedResponse actual) {
        CapturedExchange original = ewp.exchange();
        CapturedResponse promoted = new CapturedResponse(
                actual.statusCode(),
                original.response().headers(),
                actual.body()
        );
        CapturedExchange updated = new CapturedExchange(
                original.id(),
                original.capturedAt(),
                original.request(),
                promoted
        );
        Path path = ewp.sourcePath();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), updated);
            log.info("Promoted [{} {}] in {}", original.request().method(), original.request().uri(), path.getFileName());
        } catch (IOException e) {
            log.error("Failed to promote exchange file {}: {}", path, e.getMessage());
        }
    }
}
