package http.to.ems.messager;

import com.tibco.tibjms.TibjmsConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connection pool with factory caching for EMS.
 * Caches ConnectionFactory per (serverUrl, user) and pools Connections per endpoint.
 */
public class EmsConnectionPool {

    private static final Logger LOG = Logger.getLogger(EmsConnectionPool.class.getName());
    private static final int DEFAULT_MAX_CONNECTIONS_PER_KEY = 10;
    private static final int DEFAULT_MAX_IDLE_PER_KEY = 5;

    private final int maxConnectionsPerKey;
    private final int maxIdlePerKey;
    private final ConcurrentHashMap<String, CachedFactory> factoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PoolEntry> poolByKey = new ConcurrentHashMap<>();

    public EmsConnectionPool() {
        this(DEFAULT_MAX_CONNECTIONS_PER_KEY, DEFAULT_MAX_IDLE_PER_KEY);
    }

    public EmsConnectionPool(int maxConnectionsPerKey, int maxIdlePerKey) {
        this.maxConnectionsPerKey = maxConnectionsPerKey;
        this.maxIdlePerKey = maxIdlePerKey;
    }

    /**
     * Get a Connection from the pool or create a new one.
     * Caller must call {@link #returnConnection} when done, or discard on error.
     */
    public Connection getConnection(String serverUrl, String user, String password) throws JMSException {
        String key = poolKey(serverUrl, user, password);
        PoolEntry entry = poolByKey.computeIfAbsent(key, k -> new PoolEntry(key));
        return entry.borrow(serverUrl, user, password);
    }

    /**
     * Return a Connection to the pool for reuse.
     */
    public void returnConnection(Connection connection, String serverUrl, String user, String password) {
        if (connection == null) return;
        String key = poolKey(serverUrl, user, password);
        PoolEntry entry = poolByKey.get(key);
        if (entry != null) {
            entry.release(connection);
        } else {
            closeQuietly(connection);
        }
    }

    /**
     * Discard a broken connection (do not return to pool).
     */
    public void discardConnection(Connection connection, String serverUrl, String user, String password) {
        if (connection == null) return;
        String key = poolKey(serverUrl, user, password);
        PoolEntry entry = poolByKey.get(key);
        if (entry != null) {
            entry.discard(connection);
        }
        closeQuietly(connection);
    }

    private static String poolKey(String serverUrl, String user, String password) {
        String pwd = password != null ? password : "";
        return serverUrl + "|" + user + "|" + pwd;
    }

    private Connection createConnection(String serverUrl, String user, String password) throws JMSException {
        String factoryKey = poolKey(serverUrl, user, password);
        CachedFactory cf = factoryCache.computeIfAbsent(
            factoryKey,
            k -> new CachedFactory(serverUrl, user, password)
        );
        return cf.createConnection();
    }

    private void closeQuietly(Connection c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Close connection: " + e.getMessage());
        }
    }

    private class PoolEntry {
        final String key;
        final BlockingQueue<Connection> available = new LinkedBlockingQueue<>();
        final AtomicInteger activeCount = new AtomicInteger(0);

        PoolEntry(String key) {
            this.key = key;
        }

        Connection borrow(String serverUrl, String user, String password) throws JMSException {
            Connection c = available.poll();
            if (c != null) {
                try {
                    c.start();
                } catch (JMSException e) {
                    closeQuietly(c);
                    return borrow(serverUrl, user, password);
                }
                return c;
            }
            if (activeCount.get() < maxConnectionsPerKey) {
                activeCount.incrementAndGet();
                return createConnection(serverUrl, user, password);
            }
            try {
                c = available.poll(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (c != null) {
                    c.start();
                    return c;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new JMSException("Interrupted waiting for connection");
            }
            activeCount.incrementAndGet();
            c = createConnection(serverUrl, user, password);
            c.start();
            return c;
        }

        void release(Connection c) {
            if (c == null) return;
            if (available.size() < maxIdlePerKey) {
                available.offer(c);
            } else {
                activeCount.decrementAndGet();
                closeQuietly(c);
            }
        }

        void discard(Connection c) {
            activeCount.decrementAndGet();
        }
    }

    private static class CachedFactory {
        private final TibjmsConnectionFactory factory;

        CachedFactory(String serverUrl, String user, String password) {
            this.factory = new TibjmsConnectionFactory(serverUrl);
            factory.setUserName(user);
            if (password != null && !password.isEmpty()) {
                factory.setUserPassword(password);
            }
        }

        Connection createConnection() throws JMSException {
            return factory.createConnection();
        }
    }
}
