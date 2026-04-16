package com.werfen.masterlist.replayer.comparison;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XmlComparatorTest {

    private final XmlComparator comparator = new XmlComparator();

    // -----------------------------------------------------------------------
    // Positive cases
    // -----------------------------------------------------------------------

    @Test
    void identicalXmlPasses() {
        String xml = "<order><id>1</id><name>Book</name></order>";
        assertThat(comparator.compare(xml, xml)).isEmpty();
    }

    @Test
    void whitespaceIgnored() {
        String expected = "<order><id>1</id></order>";
        String actual   = "<order>\n  <id>  1  </id>\n</order>";
        assertThat(comparator.compare(expected, actual)).isEmpty();
    }

    @Test
    void commentsIgnored() {
        String expected = "<order><id>1</id></order>";
        String actual   = "<order><!-- generated --><id>1</id></order>";
        assertThat(comparator.compare(expected, actual)).isEmpty();
    }

    @Test
    void elementOrderDifferentPasses() {
        // checkForSimilar + byNameAndText matcher handles reordering
        String expected = "<order><name>Book</name><qty>2</qty></order>";
        String actual   = "<order><qty>2</qty><name>Book</name></order>";
        assertThat(comparator.compare(expected, actual)).isEmpty();
    }

    @Test
    void bothBodiesEmptyPasses() {
        assertThat(comparator.compare(null, null)).isEmpty();
        assertThat(comparator.compare("", "")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Negative cases
    // -----------------------------------------------------------------------

    @Test
    void differentTextValueFails() {
        String expected = "<order><name>Book</name></order>";
        String actual   = "<order><name>Pen</name></order>";
        assertThat(comparator.compare(expected, actual)).isNotEmpty();
    }

    @Test
    void missingElementFails() {
        String expected = "<order><id>1</id><name>Book</name></order>";
        String actual   = "<order><id>1</id></order>";
        assertThat(comparator.compare(expected, actual)).isNotEmpty();
    }

    @Test
    void oneBodyEmptyFails() {
        assertThat(comparator.compare("<a/>", null)).isNotEmpty();
        assertThat(comparator.compare(null, "<a/>")).isNotEmpty();
    }

    @Test
    void malformedXmlReturnsError() {
        assertThat(comparator.compare("<order><id>1</id></order>", "not-xml")).isNotEmpty();
    }
}
