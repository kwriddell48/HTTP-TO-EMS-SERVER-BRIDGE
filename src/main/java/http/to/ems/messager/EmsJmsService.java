package http.to.ems.messager;

import javax.jms.*;
import com.tibco.tibjms.TibjmsConnectionFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connects to TIBCO EMS and sends messages to JMS-QU1.
 * Publish-only: returns EMS send ID. Request-reply: waits for reply (async) and returns reply body.
 */
public class EmsJmsService {

    private static final Logger LOG = Logger.getLogger(EmsJmsService.class.getName());
    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    /** Header keys: first character uppercase, rest lowercase (map key convention). */
    private static final Set<String> CONTROL_HEADERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "Jms-usr", "Jms-url", "Jms-qu1", "Jms-qu2", "Jms-psw", "Jms-publish-only",
        "Jms-timeout", "Jms-correlation-id", "Statistics", "Debug",
        "Content-type", "Accept", "Content-length", "Host", "Connection", "User-agent"
    )));

    /** Optional JMS message properties (keys: first char uppercase, rest lowercase). */
    private static final Set<String> OPTIONAL_JMS_PROPERTIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "Jmsdeliverymode", "Jmstype", "Jmsexpiration", "Jmspriority", "Jmsdeliverytime",
        "Jms_tibco_compress", "Jms_tibco_preserve_undelivered"
    )));

    /** TIBCO EMS / JMS property limits (from TIBCO EMS documentation). */
    private static final int MAX_CORRELATION_ID_BYTES = 4096;   /* TIBCO EMS: recommended max 4 KB */
    private static final int MAX_JMSTYPE_LENGTH = 255;           /* JMS message type identifier */
    private static final int JMSPRIORITY_MIN = 0;
    private static final int JMSPRIORITY_MAX = 9;                /* TIBCO EMS: 0-9 */
    private static final long JMS_EXPIRATION_MIN = 0L;           /* 0 = no expire */
    private static final int MAX_PROPERTY_NAME_LENGTH = 256;
    private static final int MAX_PROPERTY_VALUE_BYTES = 4096;    /* align with correlation ID limit */

    private final MessageMetrics metrics;

    public EmsJmsService(MessageMetrics metrics) {
        this.metrics = metrics;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] EmsJmsService created", java.time.Instant.now());
        }
    }

    /**
     * Result of EMS operation: either success with body and status, or error with status and message.
     */
    public static class Result {
        public final int status;
        public final String body;
        public final String contentType;

        public Result(int status, String body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }
    }

    /**
     * Send message to EMS. Publish-only returns send ID; request-reply waits for reply (async).
     */
    public Result send(
            String serverUrl,
            String user,
            String password,
            String queue1,
            String queue2Reply,
            boolean publishOnly,
            long timeoutMs,
            String correlationId,
            String messageBody,
            Map<String, String> requestHeaders,
            boolean debugEnabled
    ) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] send entry serverUrl={1} queue1={2} publishOnly={3} timeoutMs={4} correlationId={5} bodyLength={6}",
                new Object[]{java.time.Instant.now(), serverUrl, queue1, publishOnly, timeoutMs, correlationId, messageBody != null ? messageBody.length() : 0});
        }
        if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
        Connection connection = null;
        Session session = null;
        try {
            TibjmsConnectionFactory factory = new TibjmsConnectionFactory(serverUrl);
            factory.setUserName(user);
            if (password != null && !password.isEmpty()) {
                factory.setUserPassword(password);
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Connecting to EMS", java.time.Instant.now());
            }
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destQu1 = session.createQueue(queue1);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Connected, session created, destQu1={1}", new Object[]{java.time.Instant.now(), queue1});
            }

            if (publishOnly) {
                return sendPublishOnly(session, destQu1, correlationId, messageBody, requestHeaders, debugEnabled);
            } else {
                return sendRequestReply(session, connection, destQu1, queue2Reply, timeoutMs, correlationId, messageBody, requestHeaders, debugEnabled);
            }
        } catch (JMSException e) {
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] EMS connection/send failed: {1}", new Object[]{java.time.Instant.now(), e.getMessage()});
            }
            metrics.incrementErrors();
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new Result(503, formatError(503, msg, requestHeaders), inferContentType(requestHeaders));
        } finally {
            closeQuietly(session);
            closeQuietly(connection);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] send exit connection closed", java.time.Instant.now());
            }
        }
    }

    private Result sendPublishOnly(Session session, Destination destQu1, String correlationId, String messageBody,
                                   Map<String, String> requestHeaders, boolean debugEnabled) throws JMSException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] sendPublishOnly entry correlationId={1}", new Object[]{java.time.Instant.now(), correlationId});
        }
        MessageProducer producer = null;
        try {
            producer = session.createProducer(destQu1);
            TextMessage msg = session.createTextMessage(messageBody != null ? messageBody : "");
            setCorrelationId(msg, correlationId);
            setJmsPropertiesFromHeaders(msg, requestHeaders);

            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] sendPublishOnly sending message", java.time.Instant.now());
            }
            producer.send(msg);
            metrics.incrementEmsSends();

            String messageId = msg.getJMSMessageID();
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Message sent, messageId={1}", new Object[]{java.time.Instant.now(), messageId});
            }
            boolean json = isJsonResponse(requestHeaders);
            String body = json ? "{\"messageId\":\"" + escapeJson(messageId) + "\"}" : (messageId != null ? messageId : "");
            String contentType = json ? "application/json" : "text/plain";
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] sendPublishOnly done messageId={1}", new Object[]{java.time.Instant.now(), messageId});
            }
            return new Result(200, body, contentType);
        } finally {
            closeQuietly(producer);
        }
    }

    private Result sendRequestReply(Session session, Connection connection, Destination destQu1, String queue2Reply,
                                    long timeoutMs, String correlationId, String messageBody, Map<String, String> requestHeaders,
                                    boolean debugEnabled) throws JMSException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] sendRequestReply entry queue2Reply={1} timeoutMs={2} correlationId={3}", new Object[]{java.time.Instant.now(), queue2Reply, timeoutMs, correlationId});
        }
        Destination replyDest = queue2Reply != null && !queue2Reply.isEmpty()
            ? session.createQueue(queue2Reply)
            : session.createTemporaryQueue();
        MessageConsumer consumer = null;
        MessageProducer producer = null;
        try {
            consumer = session.createConsumer(replyDest);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] sendRequestReply consumer created for replyDest", java.time.Instant.now());
            }
            CompletableFuture<String> replyFuture = new CompletableFuture<>();
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage) {
                        String text = ((TextMessage) message).getText();
                        metrics.incrementEmsReplies();
                        if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                            LOG.log(Level.FINE, "[{0}] Reply received (listener)", java.time.Instant.now());
                        }
                        replyFuture.complete(text);
                    } else {
                        replyFuture.completeExceptionally(new JMSException("Unexpected message type"));
                    }
                } catch (JMSException e) {
                    replyFuture.completeExceptionally(e);
                }
            });

            producer = session.createProducer(destQu1);
            TextMessage msg = session.createTextMessage(messageBody != null ? messageBody : "");
            msg.setJMSReplyTo(replyDest);
            String corrId = correlationId != null && !correlationId.isEmpty() ? correlationId : defaultCorrelationId();
            setCorrelationId(msg, corrId);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] sendRequestReply request message correlationId={1}", new Object[]{java.time.Instant.now(), corrId});
            }
            setJmsPropertiesFromHeaders(msg, requestHeaders);

            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] sendRequestReply sending request to JMS-QU1", java.time.Instant.now());
            }
            producer.send(msg);
            metrics.incrementEmsSends();
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Request sent to JMS-QU1, waiting for reply timeoutMs={1}", new Object[]{java.time.Instant.now(), timeoutMs});
            }

            String replyBody = replyFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Future completed with reply", java.time.Instant.now());
            }
            String contentType = inferContentType(requestHeaders);
            return new Result(200, replyBody != null ? replyBody : "", contentType);
        } catch (TimeoutException e) {
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Timeout waiting for reply", java.time.Instant.now());
            }
            metrics.incrementErrors();
            return new Result(504, formatError(504, "Timeout waiting for reply", requestHeaders), inferContentType(requestHeaders));
        } catch (Exception e) {
            if (debugEnabled && LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] Error: {1}", new Object[]{java.time.Instant.now(), e.getMessage()});
            }
            metrics.incrementErrors();
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            int status = e.getCause() instanceof JMSException ? 503 : 504;
            return new Result(status, formatError(status, msg, requestHeaders), inferContentType(requestHeaders));
        } finally {
            closeQuietly(consumer);
            closeQuietly(producer);
        }
    }

    private void setCorrelationId(Message msg, String correlationId) throws JMSException {
        if (correlationId == null || correlationId.isEmpty()) return;
        if (correlationId.getBytes(StandardCharsets.UTF_8).length > MAX_CORRELATION_ID_BYTES) {
            throw new JMSException("JMS-CORRELATION-ID exceeds TIBCO EMS limit of " + MAX_CORRELATION_ID_BYTES + " bytes");
        }
        msg.setJMSCorrelationID(correlationId);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] setCorrelationId {1}", new Object[]{java.time.Instant.now(), correlationId});
        }
    }

    /**
     * Copy TIBCO/JMS property headers (JMSX*, JMS_*, and optional JMS message properties) onto the message.
     * Validates names/values per TIBCO EMS limits.
     */
    private void setJmsPropertiesFromHeaders(Message msg, Map<String, String> requestHeaders) throws JMSException {
        if (requestHeaders == null) return;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] setJmsPropertiesFromHeaders headerCount={1}", new Object[]{java.time.Instant.now(), requestHeaders.size()});
        }
        for (Map.Entry<String, String> e : requestHeaders.entrySet()) {
            String name = e.getKey();
            if (name == null || CONTROL_HEADERS.contains(name)) continue;
            if (name.length() > MAX_PROPERTY_NAME_LENGTH) continue;
            if (!isJmsPropertyName(name)) continue;
            String value = e.getValue();
            if (value == null) continue;
            try {
                if (setOptionalJmsProperty(msg, name, value)) {
                    continue;
                }
                String emsName = toEmsPropertyName(name);
                if ("JMSXGroupSeq".equals(emsName)) {
                    int v = Integer.parseInt(value.trim());
                    msg.setIntProperty(emsName, v);
                } else {
                    if (value.getBytes(StandardCharsets.UTF_8).length > MAX_PROPERTY_VALUE_BYTES) continue;
                    msg.setStringProperty(emsName, value);
                }
            } catch (IllegalArgumentException | JMSException ex) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "Skip invalid JMS property {0}={1}: {2}", new Object[]{name, value, ex.getMessage()});
                }
            }
        }
    }

    /**
     * Set optional JMS message properties (JMSDeliveryMode, JMSType, etc.) with TIBCO EMS–compliant validation.
     * Name is normalized key (first char uppercase, rest lowercase). Returns true if handled and value is valid.
     */
    private boolean setOptionalJmsProperty(Message msg, String name, String value) throws JMSException {
        if (!OPTIONAL_JMS_PROPERTIES.contains(name)) return false;
        String v = value != null ? value.trim() : "";
        if ("Jmsdeliverymode".equals(name)) {
            int mode;
            if ("NON_PERSISTENT".equalsIgnoreCase(v) || "1".equals(v)) {
                mode = DeliveryMode.NON_PERSISTENT;
            } else if ("PERSISTENT".equalsIgnoreCase(v) || "2".equals(v)) {
                mode = DeliveryMode.PERSISTENT;
            } else {
                throw new IllegalArgumentException("JMSDeliveryMode must be PERSISTENT, NON_PERSISTENT, 1, or 2");
            }
            msg.setJMSDeliveryMode(mode);
            return true;
        }
        if ("Jmstype".equals(name)) {
            if (v.length() > MAX_JMSTYPE_LENGTH) {
                throw new IllegalArgumentException("JMSType length exceeds " + MAX_JMSTYPE_LENGTH + " characters");
            }
            msg.setJMSType(v);
            return true;
        }
        if ("Jmsexpiration".equals(name)) {
            long exp = Long.parseLong(v);
            if (exp < JMS_EXPIRATION_MIN) {
                throw new IllegalArgumentException("JMSExpiration must be >= 0 (milliseconds)");
            }
            msg.setJMSExpiration(exp);
            return true;
        }
        if ("Jmspriority".equals(name)) {
            int p = Integer.parseInt(v);
            if (p < JMSPRIORITY_MIN || p > JMSPRIORITY_MAX) {
                throw new IllegalArgumentException("JMSPriority must be between " + JMSPRIORITY_MIN + " and " + JMSPRIORITY_MAX);
            }
            msg.setJMSPriority(p);
            return true;
        }
        if ("Jmsdeliverytime".equals(name)) {
            long t = Long.parseLong(v);
            if (t < 0) {
                throw new IllegalArgumentException("JMSDeliveryTime must be >= 0 (milliseconds)");
            }
            msg.setLongProperty("JMSDeliveryTime", t);
            return true;
        }
        if ("Jms_tibco_compress".equals(name) || "Jms_tibco_preserve_undelivered".equals(name)) {
            if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                throw new IllegalArgumentException(name + " must be true or false");
            }
            msg.setStringProperty(toEmsPropertyName(name), v);
            return true;
        }
        return false;
    }

    /** Default JMS correlation ID when none provided: hostname concatenated with UUID. */
    private static String defaultCorrelationId() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) { }
        String id = host + "-" + UUID.randomUUID().toString();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] defaultCorrelationId host={1} id={2}", new Object[]{java.time.Instant.now(), host, id});
        }
        return id;
    }

    /** Map normalized header key (first char upper, rest lower) to JMS/EMS property name for API calls. */
    private static String toEmsPropertyName(String normalizedKey) {
        if (normalizedKey == null) return null;
        if ("Jmsxgroupseq".equals(normalizedKey)) return "JMSXGroupSeq";
        if ("Jms_tibco_compress".equals(normalizedKey)) return "JMS_TIBCO_COMPRESS";
        if ("Jms_tibco_preserve_undelivered".equals(normalizedKey)) return "JMS_TIBCO_PRESERVE_UNDELIVERED";
        return normalizedKey;
    }

    private boolean isJmsPropertyName(String name) {
        return (name != null) && (name.startsWith("Jmsx") || name.startsWith("Jms_") || OPTIONAL_JMS_PROPERTIES.contains(name));
    }

    private static String inferContentType(Map<String, String> headers) {
        if (headers == null) return "text/plain";
        String ct = headers.get("Content-type");
        if (ct != null && ct.toLowerCase().contains("application/json")) return "application/json";
        String accept = headers.get("Accept");
        if (accept != null && accept.toLowerCase().contains("application/json")) return "application/json";
        return "text/plain";
    }

    private boolean isJsonResponse(Map<String, String> requestHeaders) {
        return "application/json".equals(inferContentType(requestHeaders));
    }

    private String formatError(int status, String message, Map<String, String> requestHeaders) {
        boolean json = isJsonResponse(requestHeaders);
        if (json) {
            return "{\"error\":\"" + escapeJson(message) + "\",\"status\":" + status + "}";
        }
        return "Error " + status + ": " + message;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "[{0}] closeQuietly exception: {1}", new Object[]{java.time.Instant.now(), e.getMessage()});
            }
        }
    }
}
