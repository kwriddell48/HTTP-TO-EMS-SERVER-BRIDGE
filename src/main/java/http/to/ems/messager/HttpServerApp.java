package http.to.ems.messager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server that validates JMS headers, handles GET/POST, STATISTICS=YES, and delegates to EmsJmsService.
 */
public class HttpServerApp {

    private static final Logger LOG = Logger.getLogger(HttpServerApp.class.getName());
    private static final int DEFAULT_PORT = 8080;

    /** Header keys: first character uppercase, rest lowercase (map key convention). */
    private static final Set<String> CONTROL_HEADERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "Jms-usr", "Jms-url", "Jms-qu1", "Jms-qu2", "Jms-psw", "Jms-publish-only",
        "Jms-timeout", "Jms-correlation-id", "Statistics", "Debug",
        "Content-type", "Accept", "Content-length", "Host", "Connection", "User-agent"
    )));

    private final int port;
    private final MessageMetrics metrics;
    private final EmsJmsService emsService;
    private HttpServer server;

    public HttpServerApp(int port) {
        this.port = port;
        this.metrics = new MessageMetrics();
        this.emsService = new EmsJmsService(metrics);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] HttpServerApp created port={1}", new Object[]{java.time.Instant.now(), port});
        }
    }

    public void start() {
        try {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Starting HTTP server port={1}", new Object[]{java.time.Instant.now(), port});
            }
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handle);
            server.createContext("/api", this::handle);
            server.createContext("/metrics", this::handleMetrics);
            server.createContext("/stats", this::handleMetrics);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] HTTP server started", java.time.Instant.now());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to start HTTP server", e);
        }
    }

    public void stop() {
        if (server != null) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Stopping HTTP server", java.time.Instant.now());
            }
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] handle {1} {2}", new Object[]{java.time.Instant.now(), method, exchange.getRequestURI()});
        }
        if (!"GET".equals(method) && !"POST".equals(method)) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Method not allowed: {1}", new Object[]{java.time.Instant.now(), method});
            }
            metrics.incrementErrors();
            Map<String, String> h = getRequestHeadersMap(exchange);
            String errBody = formatError(405, "Method Not Allowed", h);
            sendResponse(exchange, 405, errBody, inferContentType(h), h);
            return;
        }

        metrics.incrementReceived();
        Map<String, String> headers = getRequestHeadersMap(exchange);
        boolean debugEnabled = "YES".equalsIgnoreCase(headers.getOrDefault(normalizeHeaderKey("DEBUG"), ""));

        if (debugEnabled && LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] Request received: {1} {2}", new Object[]{java.time.Instant.now(), method, exchange.getRequestURI()});
        }

        // STATISTICS=YES: return stats only, override mandatory fields
        if ("YES".equalsIgnoreCase(headers.getOrDefault(normalizeHeaderKey("STATISTICS"), ""))) {
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Returning stats only", java.time.Instant.now());
            }
            String statsBody = formatStats(headers);
            String contentType = inferContentType(headers);
            metrics.incrementProcessed();
            sendResponse(exchange, 200, statsBody, contentType, headers);
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Stats response sent", java.time.Instant.now());
            }
            return;
        }

        // Mandatory headers (keys: first char uppercase, rest lowercase)
        String jmsUsr = headers.get(normalizeHeaderKey("JMS-USR"));
        String jmsUrl = headers.get(normalizeHeaderKey("JMS-URL"));
        String jmsQu1 = headers.get(normalizeHeaderKey("JMS-QU1"));
        List<String> missing = new java.util.ArrayList<>();
        if (isEmptyOrBlank(jmsUsr)) missing.add("JMS-USR");
        if (isEmptyOrBlank(jmsUrl)) missing.add("JMS-URL");
        if (isEmptyOrBlank(jmsQu1)) missing.add("JMS-QU1");
        if (!missing.isEmpty()) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Missing headers: {1}", new Object[]{java.time.Instant.now(), missing});
            }
            metrics.incrementErrors();
            String errorBody = formatError(400, "Missing headers: " + String.join(", ", missing), headers);
            sendResponse(exchange, 400, errorBody, inferContentType(headers), headers);
            return;
        }

        // Message body: read from HTTP request body for both GET and POST
        String messageBody = readRequestBody(exchange);
        if (debugEnabled && LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] Body read, length={1}, Content-Type={2}", new Object[]{java.time.Instant.now(), messageBody.length(), headers.get(normalizeHeaderKey("Content-Type"))});
        }

        String jmsPsw = headers.get(normalizeHeaderKey("JMS-PSW"));
        String jmsQu2 = headers.get(normalizeHeaderKey("JMS-QU2"));
        boolean publishOnly = "YES".equalsIgnoreCase(headers.getOrDefault(normalizeHeaderKey("JMS-PUBLISH-ONLY"), ""));
        long timeoutMs = DEFAULT_TIMEOUT_MS;
        try {
            String t = headers.get(normalizeHeaderKey("JMS-TIMEOUT"));
            if (t != null && !t.trim().isEmpty()) timeoutMs = Long.parseLong(t.trim());
        } catch (NumberFormatException ignored) { }
        String correlationId = headers.get(normalizeHeaderKey("JMS-CORRELATION-ID"));
        if (isEmptyOrBlank(correlationId)) {
            correlationId = defaultCorrelationId();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] No JMS-CORRELATION-ID provided, using default: {1}", new Object[]{java.time.Instant.now(), correlationId});
            }
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] Calling EMS send publishOnly={1} timeoutMs={2} correlationId={3}", new Object[]{java.time.Instant.now(), publishOnly, timeoutMs, correlationId});
        }
        EmsJmsService.Result result = emsService.send(
            jmsUrl, jmsUsr, jmsPsw, jmsQu1, jmsQu2, publishOnly, timeoutMs,
            correlationId, messageBody, headers, debugEnabled
        );

        if (result.status == 200) {
            metrics.incrementReturnMessage();
            metrics.incrementProcessed();
        } else {
            metrics.incrementErrors();
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] EMS result status={1} bodyLength={2}", new Object[]{java.time.Instant.now(), result.status, result.body != null ? result.body.length() : 0});
        }
        sendResponse(exchange, result.status, result.body, result.contentType, headers);
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] handleMetrics {1}", new Object[]{java.time.Instant.now(), exchange.getRequestURI()});
        }
        Map<String, String> headers = getRequestHeadersMap(exchange);
        if (!"GET".equals(exchange.getRequestMethod())) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Metrics: method not allowed", java.time.Instant.now());
            }
            metrics.incrementErrors();
            sendResponse(exchange, 405, formatError(405, "Method Not Allowed", headers), inferContentType(headers), headers);
            return;
        }
        metrics.incrementReceived();
        metrics.incrementProcessed();
        String body = formatStats(headers);
        String contentType = inferContentType(headers);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] Metrics response sent", java.time.Instant.now());
        }
        sendResponse(exchange, 200, body, contentType, headers);
    }

    private String formatStats(Map<String, String> headers) {
        boolean json = inferContentType(headers).equals("application/json");
        return json ? metrics.toJson() : metrics.toPlainText();
    }

    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) != -1) {
                buf.write(b, 0, n);
            }
            String body = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] readRequestBody length={1}", new Object[]{java.time.Instant.now(), body.length()});
            }
            return body;
        }
    }

    private static boolean isEmptyOrBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Default JMS correlation ID when none provided: hostname concatenated with UUID. */
    private static String defaultCorrelationId() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) { }
        String id = host + "-" + java.util.UUID.randomUUID().toString();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] defaultCorrelationId generated host={1} id={2}", new Object[]{java.time.Instant.now(), host, id});
        }
        return id;
    }

    /** Normalize header key: first character uppercase, rest lowercase. */
    private static String normalizeHeaderKey(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }

    private Map<String, String> getRequestHeadersMap(HttpExchange exchange) {
        Map<String, String> map = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (name != null && !values.isEmpty()) {
                map.put(normalizeHeaderKey(name), values.get(0));
            }
        });
        return map;
    }

    private String inferContentType(Map<String, String> headers) {
        String ct = headers.get(normalizeHeaderKey("Content-Type"));
        if (ct != null && ct.toLowerCase().contains("application/json")) return "application/json";
        String accept = headers.get(normalizeHeaderKey("Accept"));
        if (accept != null && accept.toLowerCase().contains("application/json")) return "application/json";
        return "text/plain";
    }

    private String formatError(int status, String message, Map<String, String> headers) {
        boolean json = inferContentType(headers).equals("application/json");
        if (json) {
            return "{\"error\":\"" + escapeJson(message) + "\",\"status\":" + status + "}";
        }
        return "Error " + status + ": " + message;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void sendResponse(HttpExchange exchange, int status, String body, String contentType, Map<String, String> requestHeaders) throws IOException {
        byte[] bytes = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] sendResponse status={1} contentType={2} bodyBytes={3}", new Object[]{java.time.Instant.now(), status, contentType, bytes.length});
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    public MessageMetrics getMetrics() {
        return metrics;
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] main starting port={1}", new Object[]{java.time.Instant.now(), port});
        }
        HttpServerApp app = new HttpServerApp(port);
        app.start();
        System.out.println("Server listening on port " + port);
    }
}
