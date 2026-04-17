package com.werfen.replayer.runner;

import com.werfen.replayer.comparison.ComparisonResult;
import com.werfen.replayer.model.CapturedExchange;
import com.werfen.replayer.report.ReportPrinter;
import com.werfen.replayer.service.ExchangeLoader;
import com.werfen.replayer.service.RequestReplayer;
import com.werfen.replayer.service.ResponseComparator;
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

    public ReplayRunner(ExchangeLoader loader,
                        RequestReplayer replayer,
                        ResponseComparator comparator,
                        ReportPrinter printer) {
        this.loader = loader;
        this.replayer = replayer;
        this.comparator = comparator;
        this.printer = printer;
    }

    @Override
    public void run(String... args) throws Exception {
        List<ComparisonResult> results = new ArrayList<>();

        // stream() reads one exchange at a time — supports large directories without OOM
        try (Stream<CapturedExchange> exchanges = loader.stream()) {
            exchanges.forEach(exchange -> {
                log.info("Replaying [{} {}]",
                        exchange.request().method(), exchange.request().uri());
                RequestReplayer.ReplayedResponse actual = replayer.replay(exchange.request());
                ComparisonResult result = comparator.compare(exchange, actual);
                results.add(result);
                if (!result.passed()) {
                    printer.printFailure(result);
                }
            });
        }

        log.info("Replayed {} exchange(s).", results.size());
        printer.printSummary(results);

        if (results.stream().anyMatch(r -> !r.passed())) {
            System.exit(1);
        }
    }
}
