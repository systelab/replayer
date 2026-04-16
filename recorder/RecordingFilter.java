package com.werfen.masterlist.recorder;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Standalone JEE Servlet Filter that records HTTP exchanges to JSON files.
 *
 * <p>Zero external dependencies — only {@code jakarta.servlet-api} required at compile time.
 *
 * <h2>web.xml registration</h2>
 * <pre>{@code
 * <filter>
 *   <filter-name>RecordingFilter</filter-name>
 *   <filter-class>com.werfen.recorder.RecordingFilter</filter-class>
 *   <init-param>
 *     <param-name>outputDirectory</param-name>
 *     <param-value>/var/recordings</param-value>
 *   </init-param>
 * </filter>
 * <filter-mapping>
 *   <filter-name>RecordingFilter</filter-name>
 *   <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * }</pre>
 *
 * <p>If {@code outputDirectory} init-param is absent, falls back to the
 * {@code recorder.outputDirectory} system property, then to {@code <tmpdir>/recordings}.
 *
 * <h2>Legacy javax.servlet note</h2>
 * If your target application uses {@code javax.servlet} instead of {@code jakarta.servlet},
 * replace all {@code jakarta.servlet} imports with {@code javax.servlet} equivalents.
 */
public class RecordingFilter implements Filter {

    private static final DateTimeFormatter FILENAME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private String outputDirectory;

    @Override
    public void init(FilterConfig config) throws ServletException {
        outputDirectory = config.getInitParameter("outputDirectory");
        if (outputDirectory == null || outputDirectory.isBlank()) {
            outputDirectory = System.getProperty("recorder.outputDirectory",
                    System.getProperty("java.io.tmpdir") + "/recordings");
        }
        try {
            Files.createDirectories(Path.of(outputDirectory));
        } catch (IOException e) {
            throw new ServletException(
                    "Cannot create recording directory: " + outputDirectory, e);
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        BufferingRequestWrapper  wrappedReq = new BufferingRequestWrapper(httpReq);
        BufferingResponseWrapper wrappedRes = new BufferingResponseWrapper(httpRes);

        chain.doFilter(wrappedReq, wrappedRes);

        writeExchange(wrappedReq, wrappedRes);
        wrappedRes.copyBodyToResponse();
    }

    @Override
    public void destroy() {}

    // -------------------------------------------------------------------------
    // File writing
    // -------------------------------------------------------------------------

    private void writeExchange(BufferingRequestWrapper req,
                               BufferingResponseWrapper res) throws IOException {
        String timestamp  = FILENAME_FMT.format(LocalDateTime.now());
        String method     = req.getMethod();
        String sanitized  = req.getRequestURI().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filename   = timestamp + "_" + method + "_" + sanitized + ".json";

        String json = buildJson(req, res);
        Files.writeString(
                Path.of(outputDirectory, filename),
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
    }

    private String buildJson(BufferingRequestWrapper req, BufferingResponseWrapper res) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": \"").append(UUID.randomUUID()).append("\",\n");
        sb.append("  \"capturedAt\": \"").append(java.time.Instant.now()).append("\",\n");

        // request
        sb.append("  \"request\": {\n");
        sb.append("    \"uri\": \"").append(escapeJson(req.getRequestURI())).append("\",\n");
        sb.append("    \"method\": \"").append(escapeJson(req.getMethod())).append("\",\n");
        sb.append("    \"headers\": ").append(headersToJson(req)).append(",\n");
        sb.append("    \"body\": \"").append(escapeJson(req.getBodyAsString())).append("\"\n");
        sb.append("  },\n");

        // response
        sb.append("  \"response\": {\n");
        sb.append("    \"status\": ").append(res.getStatus()).append(",\n");
        sb.append("    \"headers\": ").append(responseHeadersToJson(res)).append(",\n");
        sb.append("    \"body\": \"").append(escapeJson(res.getBodyAsString())).append("\"\n");
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String headersToJson(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder("{");
        Enumeration<String> names = req.getHeaderNames();
        boolean first = true;
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (!first) sb.append(", ");
                sb.append("\"").append(escapeJson(name)).append("\": \"")
                  .append(escapeJson(req.getHeader(name))).append("\"");
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String responseHeadersToJson(HttpServletResponse res) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String name : res.getHeaderNames()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(escapeJson(name)).append("\": \"")
              .append(escapeJson(res.getHeader(name))).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // -------------------------------------------------------------------------
    // Inner class: BufferingRequestWrapper
    // -------------------------------------------------------------------------

    /**
     * Reads the request body into a byte array on construction so it can be
     * replayed to downstream handlers via {@link #getInputStream()}.
     */
    private static class BufferingRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferingRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            body = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(new ByteArrayInputStream(body));
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        String getBodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: BufferingResponseWrapper
    // -------------------------------------------------------------------------

    /**
     * Intercepts the response output stream so the filter can capture the body
     * before it is written to the client. Call {@link #copyBodyToResponse()} after
     * the filter chain to flush the captured bytes to the real response.
     */
    private static class BufferingResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();
        private final HttpServletResponse delegate;
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        BufferingResponseWrapper(HttpServletResponse response) {
            super(response);
            this.delegate = response;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                outputStream = new CapturingServletOutputStream(capturedBody);
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(getOutputStream(), true, StandardCharsets.UTF_8);
            }
            return writer;
        }

        String getBodyAsString() {
            return capturedBody.toString(StandardCharsets.UTF_8);
        }

        void copyBodyToResponse() throws IOException {
            if (writer != null) writer.flush();
            byte[] bytes = capturedBody.toByteArray();
            if (bytes.length > 0) {
                delegate.getOutputStream().write(bytes);
                delegate.getOutputStream().flush();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: ByteArrayServletInputStream
    // -------------------------------------------------------------------------

    private static class ByteArrayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream source;

        ByteArrayServletInputStream(ByteArrayInputStream source) {
            this.source = source;
        }

        @Override public int read() { return source.read(); }
        @Override public int read(byte[] b, int off, int len) { return source.read(b, off, len); }
        @Override public boolean isFinished() { return source.available() == 0; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener listener) {}
    }

    // -------------------------------------------------------------------------
    // Inner class: CapturingServletOutputStream
    // -------------------------------------------------------------------------

    private static class CapturingServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream buffer;

        CapturingServletOutputStream(ByteArrayOutputStream buffer) {
            this.buffer = buffer;
        }

        @Override public void write(int b) { buffer.write(b); }
        @Override public void write(byte[] b, int off, int len) { buffer.write(b, off, len); }
        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener listener) {}
    }
}
