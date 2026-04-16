package com.werfen.replayer.comparison;

import java.util.List;

public record ComparisonResult(
        boolean passed,
        String url,
        String method,
        int expectedStatus,
        int actualStatus,
        List<String> diffs
) {

    public static ComparisonResult pass(String url, String method, int status) {
        return new ComparisonResult(true, url, method, status, status, List.of());
    }

    public static ComparisonResult fail(String url, String method,
                                        int expectedStatus, int actualStatus,
                                        List<String> diffs) {
        return new ComparisonResult(false, url, method, expectedStatus, actualStatus, diffs);
    }
}
