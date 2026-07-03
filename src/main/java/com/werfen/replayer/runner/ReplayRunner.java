package com.werfen.replayer.runner;

import com.werfen.replayer.comparison.ComparisonResult;
import com.werfen.replayer.config.ReplayerProperties;
import com.werfen.replayer.report.ReportPrinter;
import com.werfen.replayer.service.ExchangeLoader;
import com.werfen.replayer.service.ExchangePromoter;
import com.werfen.replayer.service.RequestReplayer;
import com.werfen.replayer.service.ResponseComparator;
import com.werfen.replayer.service.SessionCorrelator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class ReplayRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReplayRunner.class);

    private final ExchangeLoader loader;
    private final RequestReplayer replayer;
    private final ResponseComparator comparator;
    private final ReportPrinter printer;
    private final SessionCorrelator correlator;
    private final ExchangePromoter promoter;
    private final ReplayerProperties properties;

    public ReplayRunner(ExchangeLoader loader,
        RequestReplayer replayer,
        ResponseComparator comparator,
        ReportPrinter printer,
        SessionCorrelator correlator,
        ExchangePromoter promoter,
        ReplayerProperties properties) {
        this.loader = loader;
        this.replayer = replayer;
        this.comparator = comparator;
        this.printer = printer;
        this.correlator = correlator;
        this.promoter = promoter;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        List<ComparisonResult> results = new ArrayList<>();
        int promoted = 0;

        try (Stream<ExchangeLoader.ExchangeWithPath> exchanges = loader.stream()) {
            for (var ewp : (Iterable<ExchangeLoader.ExchangeWithPath>) exchanges::iterator) {
                var exchange = ewp.exchange();
                var adjustedRequest = correlator.apply(exchange.request());
                log.info("Replaying [{} {}]", adjustedRequest.method(), adjustedRequest.uri());
                RequestReplayer.ReplayedResponse actual = replayer.replay(adjustedRequest);
                correlator.learn(exchange.response().body(), actual.body());
                ComparisonResult result = comparator.compare(exchange, actual);

                if (!result.passed() && properties.promote()) {
                    promoter.promote(ewp, actual);
                    promoted++;
                } else {
                    if (!result.passed()) {
                        printer.printFailure(result);
                    }
                    if (properties.reportTiming()) {
                        printer.printTiming(result);
                    }
                    results.add(result);
                }
            }
        }

        if (promoted > 0) {
            log.info("Promoted {} exchange(s).", promoted);
        }
        printer.printSummary(results);

        if (results.stream().anyMatch(r -> !r.passed())) {
            System.exit(1);
        }
    }
}
