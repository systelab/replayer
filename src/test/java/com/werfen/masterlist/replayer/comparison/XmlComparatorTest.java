package com.werfen.masterlist.replayer.comparison;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XmlComparatorTest {

    private final XmlComparator comparator = new XmlComparator();

    // -----------------------------------------------------------------------
    // Positive cases — no ignored fields
    // -----------------------------------------------------------------------

    @Test
    void identicalXmlPasses() {
        String xml = "<order><id>1</id><name>Book</name></order>";
        assertThat(comparator.compare(xml, xml, List.of())).isEmpty();
    }

    @Test
    void whitespaceIgnored() {
        String expected = "<order><id>1</id></order>";
        String actual   = "<order>\n  <id>  1  </id>\n</order>";
        assertThat(comparator.compare(expected, actual, List.of())).isEmpty();
    }

    @Test
    void commentsIgnored() {
        String expected = "<order><id>1</id></order>";
        String actual   = "<order><!-- generated --><id>1</id></order>";
        assertThat(comparator.compare(expected, actual, List.of())).isEmpty();
    }

    @Test
    void elementOrderDifferentPasses() {
        // checkForSimilar + byNameAndText matcher handles reordering
        String expected = "<order><name>Book</name><qty>2</qty></order>";
        String actual   = "<order><qty>2</qty><name>Book</name></order>";
        assertThat(comparator.compare(expected, actual, List.of())).isEmpty();
    }

    @Test
    void bothBodiesEmptyPasses() {
        assertThat(comparator.compare(null, null, List.of())).isEmpty();
        assertThat(comparator.compare("", "", List.of())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Positive cases — with ignored fields
    // -----------------------------------------------------------------------

    @Test
    void ignoreFieldSkipsTopLevelElement() {
        String expected = "<order><id>old-uuid</id><name>Book</name></order>";
        String actual   = "<order><id>new-uuid</id><name>Book</name></order>";
        assertThat(comparator.compare(expected, actual, List.of("id"))).isEmpty();
    }

    @Test
    void ignoreFieldSkipsNestedElement() {
        String expected = "<order><meta><createdAt>2024-01-01</createdAt></meta><name>Book</name></order>";
        String actual   = "<order><meta><createdAt>2026-04-16</createdAt></meta><name>Book</name></order>";
        assertThat(comparator.compare(expected, actual, List.of("createdAt"))).isEmpty();
    }

    @Test
    void ignoreFieldDoesNotSkipUnlistedElement() {
        // id is ignored, but name differs — should still fail
        String expected = "<order><id>old</id><name>Book</name></order>";
        String actual   = "<order><id>new</id><name>Pen</name></order>";
        assertThat(comparator.compare(expected, actual, List.of("id"))).isNotEmpty();
    }

    @Test
    void ignoreFieldDoesNotSuppressAbsentElement() {
        // The ignore list covers VALUE differences (element exists in both sides but differs).
        // If an element is entirely absent from actual, XMLUnit reports a structural
        // CHILD_NODELIST_LENGTH mismatch on the parent — which is not suppressed.
        String expected = "<order><id>123</id><name>Book</name></order>";
        String actual   = "<order><name>Book</name></order>";
        assertThat(comparator.compare(expected, actual, List.of("id"))).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // Negative cases
    // -----------------------------------------------------------------------

    @Test
    void differentTextValueFails() {
        String expected = "<order><name>Book</name></order>";
        String actual   = "<order><name>Pen</name></order>";
        assertThat(comparator.compare(expected, actual, List.of())).isNotEmpty();
    }

    @Test
    void missingElementFails() {
        String expected = "<order><id>1</id><name>Book</name></order>";
        String actual   = "<order><id>1</id></order>";
        assertThat(comparator.compare(expected, actual, List.of())).isNotEmpty();
    }

    @Test
    void oneBodyEmptyFails() {
        assertThat(comparator.compare("<a/>", null, List.of())).isNotEmpty();
        assertThat(comparator.compare(null, "<a/>", List.of())).isNotEmpty();
    }

    @Test
    void malformedXmlReturnsError() {
        assertThat(comparator.compare("<order><id>1</id></order>", "not-xml", List.of())).isNotEmpty();
    }
}
