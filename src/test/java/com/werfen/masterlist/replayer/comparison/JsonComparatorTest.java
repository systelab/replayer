package com.werfen.masterlist.replayer.comparison;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonComparatorTest {

    private final JsonComparator comparator = new JsonComparator();

    // -----------------------------------------------------------------------
    // Positive cases
    // -----------------------------------------------------------------------

    @Test
    void exactMatchPasses() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        assertThat(comparator.compare(json, json, List.of())).isEmpty();
    }

    @Test
    void lenientModeAllowsExtraActualFields() {
        String expected = "{\"name\":\"Alice\"}";
        String actual   = "{\"name\":\"Alice\",\"extra\":\"field\"}";
        assertThat(comparator.compare(expected, actual, List.of())).isEmpty();
    }

    @Test
    void ignoreFieldsSkipsConfiguredTopLevelKey() {
        String expected = "{\"name\":\"Alice\",\"id\":\"old-uuid\"}";
        String actual   = "{\"name\":\"Alice\",\"id\":\"new-uuid\"}";
        assertThat(comparator.compare(expected, actual, List.of("id"))).isEmpty();
    }

    @Test
    void ignoreFieldsSkipsNestedKey() {
        String expected = "{\"order\":{\"item\":\"book\",\"createdAt\":\"2024-01-01\"}}";
        String actual   = "{\"order\":{\"item\":\"book\",\"createdAt\":\"2026-04-16\"}}";
        assertThat(comparator.compare(expected, actual, List.of("createdAt"))).isEmpty();
    }

    @Test
    void ignoreFieldsSkipsKeyInsideArray() {
        String expected = "{\"lines\":[{\"qty\":1,\"id\":\"aaa\"},{\"qty\":2,\"id\":\"bbb\"}]}";
        String actual   = "{\"lines\":[{\"qty\":1,\"id\":\"xxx\"},{\"qty\":2,\"id\":\"yyy\"}]}";
        assertThat(comparator.compare(expected, actual, List.of("id"))).isEmpty();
    }

    @Test
    void bothBodiesEmptyPasses() {
        assertThat(comparator.compare(null, null, List.of())).isEmpty();
        assertThat(comparator.compare("", "", List.of())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Negative cases
    // -----------------------------------------------------------------------

    @Test
    void differentValueFails() {
        String expected = "{\"name\":\"Alice\"}";
        String actual   = "{\"name\":\"Bob\"}";
        assertThat(comparator.compare(expected, actual, List.of())).isNotEmpty();
    }

    @Test
    void ignoreFieldsDoesNotSkipUnlistedKey() {
        String expected = "{\"name\":\"Alice\",\"id\":\"same\"}";
        String actual   = "{\"name\":\"Bob\",\"id\":\"same\"}";
        // id is ignored, but name differs — should still fail
        assertThat(comparator.compare(expected, actual, List.of("id"))).isNotEmpty();
    }

    @Test
    void missingExpectedFieldFails() {
        String expected = "{\"name\":\"Alice\",\"role\":\"admin\"}";
        String actual   = "{\"name\":\"Alice\"}";
        // LENIENT allows extra fields in actual, but required fields in expected must exist
        assertThat(comparator.compare(expected, actual, List.of())).isNotEmpty();
    }

    @Test
    void oneBodyEmptyFails() {
        assertThat(comparator.compare("{\"a\":1}", null, List.of())).isNotEmpty();
        assertThat(comparator.compare(null, "{\"a\":1}", List.of())).isNotEmpty();
    }

    @Test
    void malformedJsonFails() {
        assertThat(comparator.compare("{\"a\":1}", "not-json", List.of())).isNotEmpty();
    }
}
