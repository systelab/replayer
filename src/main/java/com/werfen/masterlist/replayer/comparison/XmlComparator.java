package com.werfen.masterlist.replayer.comparison;

import org.springframework.stereotype.Component;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.ElementSelectors;

import java.util.ArrayList;
import java.util.List;

@Component
public class XmlComparator {

    /**
     * Compares two XML strings using XMLUnit.
     * Ignores whitespace, comments, and element ordering (checkForSimilar).
     *
     * @return empty list if similar, list of difference descriptions otherwise
     */
    public List<String> compare(String expected, String actual) {
        if (isBlank(expected) && isBlank(actual)) {
            return List.of();
        }
        if (isBlank(expected) || isBlank(actual)) {
            return List.of("One body is empty: expected=" + quote(expected)
                    + " actual=" + quote(actual));
        }

        try {
            Diff diff = DiffBuilder
                    .compare(Input.fromString(expected))
                    .withTest(Input.fromString(actual))
                    .ignoreWhitespace()
                    .ignoreComments()
                    .normalizeWhitespace()
                    .withNodeMatcher(new DefaultNodeMatcher(
                            ElementSelectors.byNameAndText,
                            ElementSelectors.byName))
                    .checkForSimilar()
                    .build();

            if (!diff.hasDifferences()) {
                return List.of();
            }

            List<String> diffs = new ArrayList<>();
            diff.getDifferences().forEach(d -> diffs.add(d.getComparison().toString()));
            return diffs;

        } catch (Exception e) {
            return List.of("XML comparison error: " + e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String quote(String s) {
        return s == null ? "<null>" : "\"" + s + "\"";
    }
}
