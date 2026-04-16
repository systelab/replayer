package com.werfen.replayer.comparison;

import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.DifferenceEvaluator;
import org.xmlunit.diff.DifferenceEvaluators;
import org.xmlunit.diff.ElementSelectors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class XmlComparator {

    /**
     * Compares two XML strings using XMLUnit.
     * Ignores whitespace, comments, element ordering (checkForSimilar),
     * and any element whose local name appears in {@code ignoreFields}.
     *
     * @return empty list if similar, list of difference descriptions otherwise
     */
    public List<String> compare(String expected, String actual, List<String> ignoreFields) {
        if (isBlank(expected) && isBlank(actual)) {
            return List.of();
        }
        if (isBlank(expected) || isBlank(actual)) {
            return List.of("One body is empty: expected=" + quote(expected)
                    + " actual=" + quote(actual));
        }

        try {
            DifferenceEvaluator evaluator = ignoreFields.isEmpty()
                    ? DifferenceEvaluators.Default
                    : DifferenceEvaluators.chain(
                            DifferenceEvaluators.Default,
                            ignoringElements(Set.copyOf(ignoreFields)));

            Diff diff = DiffBuilder
                    .compare(Input.fromString(expected))
                    .withTest(Input.fromString(actual))
                    .ignoreWhitespace()
                    .ignoreComments()
                    .normalizeWhitespace()
                    .withNodeMatcher(new DefaultNodeMatcher(
                            ElementSelectors.byNameAndText,
                            ElementSelectors.byName))
                    .withDifferenceEvaluator(evaluator)
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

    /**
     * Returns a {@link DifferenceEvaluator} that treats any difference as {@code EQUAL}
     * when it involves an element whose local name is in {@code ignoredNames}.
     *
     * <p>Detection covers two cases:
     * <ul>
     *   <li>The comparison target node itself is the element (e.g., element presence diff).</li>
     *   <li>The comparison target is a text/attribute node whose parent element is ignored
     *       (e.g., text-value diff inside {@code <id>}).</li>
     * </ul>
     */
    private static DifferenceEvaluator ignoringElements(Set<String> ignoredNames) {
        return (comparison, outcome) -> {
            if (outcome == ComparisonResult.EQUAL) {
                return outcome;
            }
            Node control = comparison.getControlDetails().getTarget();
            if (elementNameMatches(control, ignoredNames)) {
                return ComparisonResult.EQUAL;
            }
            Node test = comparison.getTestDetails().getTarget();
            if (elementNameMatches(test, ignoredNames)) {
                return ComparisonResult.EQUAL;
            }
            return outcome;
        };
    }

    /**
     * Returns {@code true} if {@code node} is an element whose local name is in
     * {@code names}, or if {@code node} is a text/attribute node whose parent element
     * local name is in {@code names}.
     */
    private static boolean elementNameMatches(Node node, Set<String> names) {
        if (node == null) return false;
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            return names.contains(node.getLocalName());
        }
        // Text node or attribute: check parent element
        Node parent = node.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            return names.contains(parent.getLocalName());
        }
        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String quote(String s) {
        return s == null ? "<null>" : "\"" + s + "\"";
    }
}
