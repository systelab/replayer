package com.werfen.replayer.comparison;

import java.util.List;

public record ComparisonResult(
        boolean passed,
        String url,
        String method,
        int expectedStatus,
        int actualStatus,
        List<String> diffs,
        // Recorded round-trip time (null if the exchange predates duration recording)
        // and the time attributed to the replay, both in milliseconds. Informational
        // only: timing never affects the pass/fail verdict.
        Long recordedDurationMillis,
        long replayedDurationMillis,
        // True when replayedDurationMillis is the target's server-internal time (reported
        // via the X-Replay-Duration-Millis header) — comparable with the recorded time.
        // False when it is the client round-trip fallback (network/TLS included).
        boolean replayedIsServerTiming
) {

    public static ComparisonResult pass(String url, String method, int status,
                                        Long recordedDurationMillis, long replayedDurationMillis,
                                        boolean replayedIsServerTiming) {
        return new ComparisonResult(true, url, method, status, status, List.of(),
                recordedDurationMillis, replayedDurationMillis, replayedIsServerTiming);
    }

    public static ComparisonResult fail(String url, String method,
                                        int expectedStatus, int actualStatus,
                                        List<String> diffs,
                                        Long recordedDurationMillis, long replayedDurationMillis,
                                        boolean replayedIsServerTiming) {
        return new ComparisonResult(false, url, method, expectedStatus, actualStatus, diffs,
                recordedDurationMillis, replayedDurationMillis, replayedIsServerTiming);
    }
}
