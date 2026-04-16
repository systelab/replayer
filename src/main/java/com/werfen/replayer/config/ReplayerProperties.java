package com.werfen.replayer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "replayer")
public record ReplayerProperties(
        String targetBaseUrl,
        String exchangesDirectory,
        List<String> ignoreFields,
        int requestTimeoutSeconds,
        String contentTypeDetection
) {}
