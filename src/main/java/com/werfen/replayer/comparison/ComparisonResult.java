package com.werfen.replayer.comparison;

import java.util.List;

public record ComparisonResult(
        boolean passed,
        String url,
        String method,
        int expectedStatus,
        int actualStatus,
        List<String> diffs,
        Long recordedDurationMillis,
        long replayedDurationMillis,
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
