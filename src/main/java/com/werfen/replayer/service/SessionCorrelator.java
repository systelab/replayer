package com.werfen.replayer.service;

import com.werfen.replayer.model.CapturedRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Learns which values changed between a captured response and the actual response,
 * and rewrites subsequent request headers / bodies so dynamic values (tokens, session
 * IDs, …) are automatically kept in sync.
 */
@Component
public class SessionCorrelator {
  private static final Logger log = LoggerFactory.getLogger(SessionCorrelator.class);
  /**
   * Minimum character length a text-node value must have to be considered for
   * substitution.  Short values (booleans, numbers, single words) are far too
   * likely to produce false-positive substring matches inside Base64-encoded
   * tokens or other opaque strings, corrupting them.
   */
  private static final int MIN_VALUE_LENGTH = 20;
  /** Maps captured (old) value → actual (new) value. */
  private final Map<String, String> substitutions = new LinkedHashMap<>();
  /**
   * Compares {@code capturedBody} with {@code actualBody}, collects all differing
   * text-node pairs and stores them as substitution rules.
   */
  public void learn(String capturedBody, String actualBody) {
    if (capturedBody == null || actualBody == null) return;
    if (capturedBody.equals(actualBody)) return;
    String trimmed = capturedBody.stripLeading();
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      try {
        extractJsonDiffs("", capturedBody, actualBody);
      } catch (Exception e) {
        log.debug("Session correlator could not parse JSON bodies, skipping learning: {}", e.getMessage());
      }
    } else {
      try {
        extractXmlDiffs(capturedBody, actualBody);
      } catch (Exception e) {
        log.debug("Session correlator could not parse XML bodies, skipping learning: {}", e.getMessage());
      }
    }
  }
  /**
   * Returns a new {@link CapturedRequest} with all known substitutions applied to
   * headers and body.  Returns the original request unchanged if nothing to substitute.
   */
  public CapturedRequest apply(CapturedRequest request) {
    if (substitutions.isEmpty()) return request;
    Map<String, String> newHeaders = null;
    if (request.headers() != null) {
      newHeaders = new LinkedHashMap<>();
      for (Map.Entry<String, String> h : request.headers().entrySet()) {
        newHeaders.put(h.getKey(), substitute(h.getValue()));
      }
    }
    boolean headersChanged = newHeaders != null && !request.headers().equals(newHeaders);
    if (headersChanged) {
      log.debug("Session correlator rewrote headers of request [{} {}]", request.method(), request.uri());
    }
    // Body is never modified: request payloads are static recorded data
    return new CapturedRequest(request.uri(), request.method(), newHeaders, request.body());
  }
  // -------------------------------------------------------------------------
  // private helpers
  // -------------------------------------------------------------------------
  private void extractJsonDiffs(String path, String captured, String actual) throws JSONException {
    String tc = captured.stripLeading();
    String ta = actual.stripLeading();
    if (tc.startsWith("{") && ta.startsWith("{")) {
      diffJsonObjects(path, new JSONObject(captured), new JSONObject(actual));
    } else if (tc.startsWith("[") && ta.startsWith("[")) {
      diffJsonArrays(path, new JSONArray(captured), new JSONArray(actual));
    }
  }
  private void diffJsonObjects(String path, JSONObject captured, JSONObject actual) throws JSONException {
    java.util.Iterator<String> keys = captured.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!actual.has(key)) continue;
      Object capturedVal = captured.get(key);
      Object actualVal   = actual.get(key);
      String childPath   = path.isEmpty() ? key : path + "." + key;
      diffJsonValues(childPath, capturedVal, actualVal);
    }
  }
  private void diffJsonArrays(String path, JSONArray captured, JSONArray actual) throws JSONException {
    int count = Math.min(captured.length(), actual.length());
    for (int i = 0; i < count; i++) {
      diffJsonValues(path + "[" + i + "]", captured.get(i), actual.get(i));
    }
  }
  private void diffJsonValues(String path, Object capturedVal, Object actualVal) throws JSONException {
    if (capturedVal instanceof JSONObject co && actualVal instanceof JSONObject ao) {
      diffJsonObjects(path, co, ao);
    } else if (capturedVal instanceof JSONArray ca && actualVal instanceof JSONArray aa) {
      diffJsonArrays(path, ca, aa);
    } else if (capturedVal instanceof String cs && actualVal instanceof String as) {
      if (!cs.equals(as) && cs.length() >= MIN_VALUE_LENGTH) {
        substitutions.put(cs, as);
        log.debug("Session correlator learned JSON substitution [{}]: [{}] -> [{}]",
            path, abbreviate(cs, 40), abbreviate(as, 40));
      }
    }
  }
  private void extractXmlDiffs(String captured, String actual) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    factory.setExpandEntityReferences(false);
    org.w3c.dom.Document capturedDoc = factory.newDocumentBuilder()
        .parse(new InputSource(new StringReader(captured)));
    org.w3c.dom.Document actualDoc = factory.newDocumentBuilder()
        .parse(new InputSource(new StringReader(actual)));
    collectTextDiffs(capturedDoc.getDocumentElement(), actualDoc.getDocumentElement());
  }
  private void collectTextDiffs(Node captured, Node actual) {
    if (captured == null || actual == null) return;
    if (captured.getNodeType() == Node.TEXT_NODE) {
      String capturedVal = captured.getNodeValue().trim();
      String actualVal   = actual.getNodeValue().trim();
      if (!capturedVal.isEmpty()
          && !actualVal.isEmpty()
          && !capturedVal.equals(actualVal)
          && capturedVal.length() >= MIN_VALUE_LENGTH) {
        substitutions.put(capturedVal, actualVal);
        log.debug("Session correlator learned substitution: [{}] -> [{}]",
            abbreviate(capturedVal, 40), abbreviate(actualVal, 40));
      }
      return;
    }
    NodeList capturedChildren = captured.getChildNodes();
    NodeList actualChildren   = actual.getChildNodes();
    int count = Math.min(capturedChildren.getLength(), actualChildren.getLength());
    for (int i = 0; i < count; i++) {
      collectTextDiffs(capturedChildren.item(i), actualChildren.item(i));
    }
  }
  /**
   * Applies all learned substitutions to {@code value}, processing longest keys
   * first so that a shorter pattern cannot corrupt the replacement of a longer one.
   */
  private String substitute(String value) {
    List<Map.Entry<String, String>> entries = new ArrayList<>(substitutions.entrySet());
    // Longest key first → prevents short patterns from matching inside longer replacements
    entries.sort((a, b) -> b.getKey().length() - a.getKey().length());
    for (Map.Entry<String, String> entry : entries) {
      value = value.replace(entry.getKey(), entry.getValue());
    }
    return value;
  }
  private static String abbreviate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }
}
