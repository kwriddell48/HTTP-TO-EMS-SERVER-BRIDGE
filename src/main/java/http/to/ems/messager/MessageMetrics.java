package http.to.ems.messager;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe counters for message processing metrics.
 */
public class MessageMetrics {

    private static final Logger LOG = Logger.getLogger(MessageMetrics.class.getName());

    private final AtomicLong received = new AtomicLong(0);
    private final AtomicLong emsSends = new AtomicLong(0);
    private final AtomicLong emsReplies = new AtomicLong(0);
    private final AtomicLong returnMessage = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    /** Successfully completed requests (HTTP 200). */
    private final AtomicLong processed = new AtomicLong(0);

    public void incrementReceived() {
        long n = received.incrementAndGet();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] incrementReceived total={1}", new Object[]{java.time.Instant.now(), n});
        }
    }

    public void incrementEmsSends() {
        long n = emsSends.incrementAndGet();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] incrementEmsSends total={1}", new Object[]{java.time.Instant.now(), n});
        }
    }

    public void incrementEmsReplies() {
        long n = emsReplies.incrementAndGet();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] incrementEmsReplies total={1}", new Object[]{java.time.Instant.now(), n});
        }
    }

    public void incrementReturnMessage() {
        long n = returnMessage.incrementAndGet();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] incrementReturnMessage total={1}", new Object[]{java.time.Instant.now(), n});
        }
    }

    public void incrementErrors() {
        long n = errors.incrementAndGet();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] incrementErrors total={1}", new Object[]{java.time.Instant.now(), n});
        }
    }

    public void incrementProcessed() {
        long n = processed.incrementAndGet();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] incrementProcessed total={1}", new Object[]{java.time.Instant.now(), n});
        }
    }

    public long getReceived() { return received.get(); }
    public long getEmsSends() { return emsSends.get(); }
    public long getEmsReplies() { return emsReplies.get(); }
    public long getReturnMessage() { return returnMessage.get(); }
    public long getErrors() { return errors.get(); }
    public long getProcessed() { return processed.get(); }

    /**
     * Snapshot for JSON export: {"received": N, "emsSends": N, ..., "processed": N}
     */
    public String toJson() {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] toJson snapshot requested", java.time.Instant.now());
        }
        return String.format(
            "{\"received\":%d,\"emsSends\":%d,\"emsReplies\":%d,\"returnMessage\":%d,\"errors\":%d,\"processed\":%d}",
            received.get(), emsSends.get(), emsReplies.get(), returnMessage.get(), errors.get(), processed.get()
        );
    }

    /**
     * Snapshot for plain text export.
     */
    public String toPlainText() {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[{0}] toPlainText snapshot requested", java.time.Instant.now());
        }
        return String.format(
            "received=%d emsSends=%d emsReplies=%d returnMessage=%d errors=%d processed=%d",
            received.get(), emsSends.get(), emsReplies.get(), returnMessage.get(), errors.get(), processed.get()
        );
    }
}
