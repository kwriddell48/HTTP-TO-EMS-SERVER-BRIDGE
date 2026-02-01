package http.to.ems.messager;

import javax.jms.*;
import com.tibco.tibjms.TibjmsConnectionFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens to a request queue, generates a reply with timestamps and location info,
 * and sends it to the message's JMSReplyTo destination.
 *
 * Flow: Request Queue (e.g., queue.request) -> this listener -> Reply Queue (from JMSReplyTo)
 */
public class EmsReplyListener {

    private static final Logger LOG = Logger.getLogger(EmsReplyListener.class.getName());

    private final String serverUrl;
    private final String user;
    private final String password;
    private final String requestQueue;
    private final AtomicLong messageCount = new AtomicLong(0);

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private volatile boolean running = true;

    public EmsReplyListener(String serverUrl, String user, String password, String requestQueue) {
        this.serverUrl = serverUrl;
        this.user = user;
        this.password = password;
        this.requestQueue = requestQueue;
    }

    public void start() throws JMSException {
        TibjmsConnectionFactory factory = new TibjmsConnectionFactory(serverUrl);
        factory.setUserName(user);
        if (password != null && !password.isEmpty()) {
            factory.setUserPassword(password);
        }

        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination dest = session.createQueue(requestQueue);
        consumer = session.createConsumer(dest);
        consumer.setMessageListener(this::onMessage);

        LOG.info(String.format("EmsReplyListener started: listening on %s", requestQueue));
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
        closeQuietly(consumer);
        closeQuietly(session);
        closeQuietly(connection);
        LOG.info("EmsReplyListener stopped");
    }

    private void onMessage(Message requestMsg) {
        long count = messageCount.incrementAndGet();
        MessageProducer replyProducer = null;

        try {
            Destination replyTo = requestMsg.getJMSReplyTo();
            if (replyTo == null) {
                LOG.warning("Message has no JMSReplyTo, cannot send reply");
                return;
            }

            String correlationId = requestMsg.getJMSCorrelationID();
            String requestMessageId = requestMsg.getJMSMessageID();
            String requestBody = "";
            if (requestMsg instanceof TextMessage) {
                requestBody = ((TextMessage) requestMsg).getText();
            }

            Instant receivedAt = Instant.now();
            String replyBody = buildReply(requestBody, requestMessageId, correlationId, receivedAt, count);

            replyProducer = session.createProducer(replyTo);
            TextMessage replyMsg = session.createTextMessage(replyBody);
            if (correlationId != null) {
                replyMsg.setJMSCorrelationID(correlationId);
            }
            replyMsg.setJMSType("Reply");

            replyProducer.send(replyMsg);

            LOG.info(String.format("[%d] Reply sent to JMSReplyTo for correlationId=%s", count, correlationId));

        } catch (JMSException e) {
            LOG.log(Level.SEVERE, "Error processing message: " + e.getMessage(), e);
        } finally {
            closeQuietly(replyProducer);
        }
    }

    private String buildReply(String originalMessage, String requestMessageId, String correlationId,
                              Instant receivedAt, long messageNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"receivedAt\": \"").append(receivedAt).append("\",\n");
        sb.append("  \"hostname\": \"").append(escapeJson(getHostname())).append("\",\n");
        sb.append("  \"location\": \"").append(escapeJson(getLocation())).append("\",\n");
        sb.append("  \"javaVersion\": \"").append(escapeJson(System.getProperty("java.version"))).append("\",\n");
        sb.append("  \"userName\": \"").append(escapeJson(System.getProperty("user.name"))).append("\",\n");
        sb.append("  \"messageNumber\": ").append(messageNumber).append(",\n");
        sb.append("  \"requestMessageId\": \"").append(escapeJson(requestMessageId != null ? requestMessageId : "")).append("\",\n");
        sb.append("  \"correlationId\": \"").append(escapeJson(correlationId != null ? correlationId : "")).append("\",\n");
        sb.append("  \"originalMessage\": \"").append(escapeJson(originalMessage != null ? originalMessage : "")).append("\",\n");
        sb.append("  \"replyFrom\": \"EmsReplyListener\"\n");
        sb.append("}");
        return sb.toString();
    }

    private static String getHostname() {
        return HostnameCache.getHostname();
    }

    private static String getLocation() {
        try {
            String host = HostnameCache.getHostname();
            String addr = InetAddress.getLocalHost().getHostAddress();
            return host + " (" + addr + ")";
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Close error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: EmsReplyListener <ems-url> <user> <password> <request-queue>");
            System.err.println("  Example: EmsReplyListener tcp://localhost:7222 admin password queue.request");
            System.exit(1);
        }

        String serverUrl = args[0];
        String user = args[1];
        String password = args[2];
        String requestQueue = args[3];

        EmsReplyListener listener = new EmsReplyListener(serverUrl, user, password, requestQueue);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received");
            listener.stop();
        }));

        try {
            listener.start();
            System.out.println("EmsReplyListener running. Press Ctrl+C to stop.");
            System.out.println("  EMS URL: " + serverUrl);
            System.out.println("  Request queue: " + requestQueue);
            System.out.println("  Hostname: " + getHostname());

            while (listener.isRunning()) {
                Thread.sleep(1000);
            }
        } catch (JMSException e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
