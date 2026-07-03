package com.werfen.replayer.report;

import com.werfen.replayer.comparison.ComparisonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportPrinter {

    private static final Logger log = LoggerFactory.getLogger(ReportPrinter.class);

    public void printFailure(ComparisonResult result) {
        log.error("FAIL  [{} {}]", result.method(), result.url());
        if (result.expectedStatus() != result.actualStatus()) {
            log.error("      Status : expected={} actual={}",
                    result.expectedStatus(), result.actualStatus());
        }
        for (String diff : result.diffs()) {
            log.error("      Diff   : {}", diff);
        }
    }

    public void printTiming(ComparisonResult result) {
        if (result.passed()) {
            log.info("PASS  [{} {}]  Time : {}",
                    result.method(), result.url(), formatTiming(result));
        } else {
            log.error("      Time   : {}", formatTiming(result));
        }
    }

    private String formatTiming(ComparisonResult result) {
        long replayed = result.replayedDurationMillis();
        Long recorded = result.recordedDurationMillis();
        // The recorded time is the original server's internal processing time. It is only
        // comparable when the replayed value is also server-internal (from the header);
        // otherwise flag it so the +diff isn't read as a real slowdown.
        String note = result.replayedIsServerTiming()
                ? ""
                : " (round-trip; deploy RecordingFilter on target for server timing)";
        if (recorded == null) {
            return "replayed=" + formatDuration(replayed) + " (no recorded time in exchange)" + note;
        }
        long diff = replayed - recorded;
        String sign = diff >= 0 ? "+" : "-";
        return "recorded=" + formatDuration(recorded)
                + " replayed=" + formatDuration(replayed)
                + " diff=" + sign + formatDuration(Math.abs(diff))
                + note;
    }

    private static String formatDuration(long millis) {
        if (Math.abs(millis) < 1000) {
            return millis + " ms";
        }
        return String.format("%.2f s", millis / 1000.0);
    }

    public void printSummary(List<ComparisonResult> results) {
        long passed = results.stream().filter(ComparisonResult::passed).count();
        long failed = results.size() - passed;

        log.info("=================================================");
        log.info("  Replay summary: {} passed, {} failed (total {})",
                passed, failed, results.size());
        log.info("=================================================");

        if (failed == 0) {
            log.info("  Result: ALL PASSED");
        } else {
            log.error("  Result: {} FAILURE(S) DETECTED", failed);
        }
    }
}
