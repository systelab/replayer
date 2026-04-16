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
