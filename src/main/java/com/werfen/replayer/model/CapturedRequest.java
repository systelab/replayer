package com.werfen.replayer.model;

import java.util.Map;

public record CapturedRequest(
        String uri,
        String method,
        Map<String, String> headers,
        String body
) {}
