package com.werfen.masterlist.replayer.model;

import java.util.Map;

public record CapturedResponse(
        int status,
        Map<String, String> headers,
        String body
) {}
