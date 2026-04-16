package com.werfen.masterlist.replayer.model;

import java.time.Instant;

public record CapturedExchange(
        String id,
        Instant capturedAt,
        CapturedRequest request,
        CapturedResponse response
) {}
