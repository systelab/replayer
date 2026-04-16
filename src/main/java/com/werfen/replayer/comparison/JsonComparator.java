package com.werfen.replayer.comparison;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class JsonComparator {

    /**
     * Compares two JSON strings using JSONAssert LENIENT mode.
     * Fields whose leaf key appears in {@code ignoreFields} are skipped at any nesting depth,
     * including inside arrays.
     *
     * @return empty list if equal, list of human-readable differences otherwise
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
            JSONAssert.assertEquals(
                    expected,
                    actual,
                    new IgnoringFieldsComparator(JSONCompareMode.LENIENT, Set.copyOf(ignoreFields))
            );
            return List.of();
        } catch (AssertionError e) {
            return List.of(e.getMessage());
        } catch (JSONException e) {
            return List.of("JSON parse error: " + e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String quote(String s) {
        return s == null ? "<null>" : "\"" + s + "\"";
    }

    /**
     * JSONAssert custom comparator that skips ignored fields at any depth.
     *
     * <p>Two mechanisms work together:
     * <ol>
     *   <li><b>Array stripping</b>: before JSONAssert's "natural-key discovery" runs on an array
     *       (which would identify a unique field like {@code id} and use it for element matching),
     *       we strip the ignored fields from all JSONObject elements in both arrays. This prevents
     *       ignored fields from being used as array-matching keys.</li>
     *   <li><b>Leaf-key skipping</b>: for non-array values, if the rightmost path segment is in
     *       the ignore set, the comparison is skipped entirely.</li>
     * </ol>
     */
    private static class IgnoringFieldsComparator extends CustomComparator {

        private final Set<String> ignoredFields;

        IgnoringFieldsComparator(JSONCompareMode mode, Set<String> ignoredFields) {
            super(mode, new Customization[0]);
            this.ignoredFields = ignoredFields;
        }

        @Override
        public void compareValues(String prefix, Object expectedValue,
                                  Object actualValue, JSONCompareResult result)
                throws JSONException {

            // For arrays: strip ignored fields from object elements before delegating.
            // This prevents JSONAssert's natural-key discovery from picking up an ignored
            // field (e.g., "id") as the array matching key.
            if (expectedValue instanceof JSONArray expectedArr
                    && actualValue instanceof JSONArray actualArr) {
                super.compareValues(prefix,
                        stripIgnored(expectedArr),
                        stripIgnored(actualArr),
                        result);
                return;
            }

            // For scalar / object values: skip comparison if the leaf key is ignored.
            if (ignoredFields.contains(leafKey(prefix))) {
                return;
            }
            super.compareValues(prefix, expectedValue, actualValue, result);
        }

        // ----------------------------------------------------------------
        // Strip helpers — recursively remove ignored fields from any
        // JSONObject (and JSONObject elements of JSONArrays).
        // ----------------------------------------------------------------

        private JSONArray stripIgnored(JSONArray array) throws JSONException {
            JSONArray out = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                Object el = array.get(i);
                if (el instanceof JSONObject obj) {
                    out.put(stripIgnored(obj));
                } else if (el instanceof JSONArray nested) {
                    out.put(stripIgnored(nested));
                } else {
                    out.put(el);
                }
            }
            return out;
        }

        private JSONObject stripIgnored(JSONObject obj) throws JSONException {
            JSONObject out = new JSONObject();
            java.util.Iterator<String> iter = obj.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                if (ignoredFields.contains(key)) {
                    continue;
                }
                Object val = obj.get(key);
                if (val instanceof JSONObject nested) {
                    out.put(key, stripIgnored(nested));
                } else if (val instanceof JSONArray arr) {
                    out.put(key, stripIgnored(arr));
                } else {
                    out.put(key, val);
                }
            }
            return out;
        }

        /**
         * Extracts the rightmost path segment from a JSONAssert prefix.
         * <ul>
         *   <li>{@code name}          → {@code name}</li>
         *   <li>{@code order.id}      → {@code id}</li>
         *   <li>{@code lines[0].id}   → {@code id}</li>
         * </ul>
         */
        private static String leafKey(String prefix) {
            int lastDot     = prefix.lastIndexOf('.');
            int lastBracket = prefix.lastIndexOf('[');
            int sep         = Math.max(lastDot, lastBracket);
            return sep >= 0 ? prefix.substring(sep + 1) : prefix;
        }
    }
}
