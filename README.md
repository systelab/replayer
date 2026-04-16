# Replayer — HTTP Record & Replay Testing Suite

Version: **1.0.0** | Java 21 | Spring Boot 3.4.4

A two-part tool for verifying that a refactored backend service produces identical responses to the original:

1. **`RecordingFilter`** — a standalone JEE Servlet Filter you drop into the *original* service to capture live traffic.
2. **Replay Runner** — a Spring Boot CLI application that replays captured exchanges against the *refactored* service and reports differences.

---

## Captured Exchange File Schema

Each recorded exchange is stored as a single JSON file:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "capturedAt": "2026-04-16T10:00:00Z",
  "request": {
    "uri": "/api/v1/orders/123",
    "method": "GET",
    "headers": {
      "Accept": "application/json",
      "Authorization": "Bearer ..."
    },
    "body": ""
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "{\"id\":\"123\",\"status\":\"CONFIRMED\",...}"
  }
}
```

Files are named `{timestamp}_{METHOD}_{uri-sanitized}.json` (e.g., `20260416_100000_123_GET__api_v1_orders_123.json`).

---

## Part 1 — Recording Traffic (RecordingFilter)

### What it captures

- Request: URI, HTTP method, all headers, request body
- Response: HTTP status, all headers, response body
- One JSON file per HTTP exchange, written atomically

### How to register the filter

#### Option A — `web.xml` (traditional JEE)

```xml
<filter>
  <filter-name>RecordingFilter</filter-name>
  <filter-class>com.werfen.recorder.RecordingFilter</filter-class>
  <init-param>
    <param-name>outputDirectory</param-name>
    <param-value>/var/recordings</param-value>
  </init-param>
</filter>
<filter-mapping>
  <filter-name>RecordingFilter</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>
```

#### Option B — Programmatic registration (Spring Boot / Servlet 3.x)

```java
@Bean
public FilterRegistrationBean<RecordingFilter> recordingFilter() {
    FilterRegistrationBean<RecordingFilter> reg = new FilterRegistrationBean<>();
    reg.setFilter(new RecordingFilter());
    reg.addUrlPatterns("/*");
    reg.addInitParameter("outputDirectory", "/var/recordings");
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return reg;
}
```

#### Option C — System property (no config change)

```bash
java -Drecorder.outputDirectory=/var/recordings -jar your-original-service.jar
```

### javax vs jakarta

If your legacy application uses `javax.servlet` (pre-Jakarta EE 9), replace all `jakarta.servlet.*` imports in `RecordingFilter.java` with `javax.servlet.*`. No other changes are required.

### Compiling the filter

The filter source lives in `recorder/RecordingFilter.java`. To compile it standalone:

```bash
javac -cp jakarta.servlet-api-6.1.0.jar recorder/RecordingFilter.java -d recorder-classes/
jar cf recorder.jar -C recorder-classes .
```

Then add `recorder.jar` to your legacy application's classpath.

---

## Part 2 — Replaying Traffic (Replay Runner)

### Prerequisites

- Java 21+
- Maven 3.9+
- The refactored service running and accessible

### Build

```bash
mvn clean package -DskipTests
```

### Configure

Edit `src/main/resources/application.yml` (or pass as environment variables / command-line args):

```yaml
replayer:
  target-base-url: http://localhost:8080   # URL of the refactored service
  exchanges-directory: /var/recordings     # directory with captured JSON files
  ignore-fields:                           # leaf field names to skip in comparison
    - id
    - createdAt
    - updatedAt
    - timestamp
    - correlationId
  request-timeout-seconds: 30
  content-type-detection: auto             # auto | json | xml
```

### Configure the Ignore List

The `ignore-fields` list accepts any **leaf field name** (the last segment of a dotted JSON path). Fields are ignored recursively at any nesting depth.

| Example path in response       | Leaf key    | Add to ignore-fields? |
|-------------------------------|-------------|------------------------|
| `id`                          | `id`        | `- id`                 |
| `order.createdAt`             | `createdAt` | `- createdAt`          |
| `order.lines[0].id`           | `id`        | `- id` (same entry)    |
| `metadata.correlationId`      | `correlationId` | `- correlationId`  |

### Run

```bash
# Using the fat JAR
java -jar target/replayer-1.0.0.jar

# Override config at runtime
java -jar target/replayer-1.0.0.jar \
  --replayer.target-base-url=http://staging.example.com \
  --replayer.exchanges-directory=/var/recordings
```

The runner exits with **code 0** (all passed) or **code 1** (one or more failures) — suitable for CI/CD pipelines.

### Sample output

```
10:05:01.234 INFO  c.w.m.r.runner.ReplayRunner - Loaded 42 exchange(s). Starting replay...
10:05:01.310 INFO  c.w.m.r.runner.ReplayRunner - Replaying [GET /api/v1/orders/123]
10:05:01.388 ERROR c.w.m.r.report.ReportPrinter - FAIL  [GET /api/v1/orders/123]
10:05:01.389 ERROR c.w.m.r.report.ReportPrinter -       Diff   : orders[0].status
                                                                   Expected: CONFIRMED
                                                                   Got: PENDING
10:05:02.100 INFO  c.w.m.r.report.ReportPrinter - =================================================
10:05:02.100 INFO  c.w.m.r.report.ReportPrinter -   Replay summary: 41 passed, 1 failed (total 42)
10:05:02.100 ERROR c.w.m.r.report.ReportPrinter -   Result: 1 FAILURE(S) DETECTED
```

---

## End-to-End Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                     RECORDING PHASE                         │
│                                                             │
│  Production/Staging traffic                                 │
│         │                                                   │
│         ▼                                                   │
│  Original Service + RecordingFilter                         │
│         │                                                   │
│         ▼                                                   │
│  /var/recordings/*.json   ◄── captured exchanges            │
└─────────────────────────────────────────────────────────────┘
                        │
                        │  copy exchange files
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     REPLAY PHASE                            │
│                                                             │
│  Reset DB to empty state                                    │
│         │                                                   │
│         ▼                                                   │
│  Replay Runner ──► Refactored Service                       │
│         │                                                   │
│         ▼                                                   │
│  Pass / Fail report + exit code                             │
└─────────────────────────────────────────────────────────────┘
```

**Important**: reset the database to an identical empty state before both the recording and replay phases so that IDs and sequences are consistent.

---

## Running Tests

```bash
mvn test
```

Tests cover:
- `JsonComparatorTest` — JSON equality, field ignoring, malformed input
- `XmlComparatorTest` — XML similarity, whitespace/comment tolerance, ordering
- `ExchangeLoaderTest` — file loading, sort order, error handling
- `ResponseComparatorTest` — status comparison, content-type routing, body sniffing
- `ReplayRunnerIntegrationTest` — full pipeline against WireMock

---

## Infrastructure Setup

See `docker-compose.yml` for a template. Add your database and any other services your application depends on, then:

```bash
docker compose up -d
# reset database to empty state
# start the original service with RecordingFilter
# generate traffic
# stop the original service
# start the refactored service
java -jar target/replayer-1.0.0.jar
```
