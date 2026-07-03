package com.werfen.replayer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "replayer")
public record ReplayerProperties(
        String targetBaseUrl,
        String exchangesDirectory,
        List<String> ignoreFields,
        int requestTimeoutSeconds,
        String contentTypeDetection,
        boolean promote,
        // When true, the recorded-vs-replayed processing time is reported per exchange.
        // Off by default: meaningful numbers require the RecordingFilter deployed on the
        // target so it reports server-internal time via the X-Replay-Duration-Millis
        // header (otherwise only the client round-trip is available, which includes
        // network/TLS overhead and is not comparable with the recorded server time).
        @DefaultValue("false") boolean reportTiming
) {}
