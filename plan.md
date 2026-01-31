# HTTP to EMS Messager - Project Plan

## Project Overview

**Project Name:** HTTP to EMS Messager  
**Package:** `http.to.ems.messager`  
**Version:** 1.0-SNAPSHOT  
**Status:** Active Development

---

## Completed Features

### Phase 1: Core Infrastructure ✅

- [x] Maven project setup with Java 8 compatibility
- [x] Package structure (`http.to.ems.messager`)
- [x] HTTP server using `com.sun.net.httpserver`
- [x] Multi-threaded request handling with cached thread pool
- [x] CORS support (`Access-Control-Allow-Origin: *`)

### Phase 2: TIBCO EMS Integration ✅

- [x] TIBCO EMS connection factory integration
- [x] JMS session management with auto-acknowledge
- [x] Publish-only messaging (fire-and-forget)
- [x] Request-reply messaging with configurable timeout
- [x] Temporary queue support for replies
- [x] Named reply queue support (`JMS-QU2`)
- [x] Connection cleanup and resource management

### Phase 3: HTTP Header Processing ✅

- [x] Required header validation (`JMS-URL`, `JMS-USR`, `JMS-QU1`)
- [x] Optional header support (`JMS-PSW`, `JMS-QU2`, `JMS-TIMEOUT`)
- [x] Header key normalization (first char uppercase, rest lowercase)
- [x] `JMS-PUBLISH-ONLY` mode toggle
- [x] `JMS-CORRELATION-ID` with auto-generation (hostname + UUID)
- [x] `STATISTICS` header for metrics-only response
- [x] `DEBUG` header for per-request debug logging

### Phase 4: JMS Property Support ✅

- [x] Standard JMS properties passthrough
  - [x] `JMSDeliveryMode` (PERSISTENT/NON_PERSISTENT)
  - [x] `JMSType` (max 255 chars)
  - [x] `JMSExpiration` (milliseconds)
  - [x] `JMSPriority` (0-9)
  - [x] `JMSDeliveryTime` (milliseconds)
- [x] TIBCO-specific properties
  - [x] `JMS_TIBCO_COMPRESS` (true/false)
  - [x] `JMS_TIBCO_PRESERVE_UNDELIVERED` (true/false)
- [x] JMSX properties (`JMSXGroupID`, `JMSXGroupSeq`)
- [x] Property validation per TIBCO EMS limits
  - [x] Correlation ID: max 4 KB
  - [x] Property name: max 256 chars
  - [x] Property value: max 4 KB

### Phase 5: Metrics & Monitoring ✅

- [x] Thread-safe metrics counters (AtomicLong)
- [x] Metrics endpoints (`/metrics`, `/stats`)
- [x] JSON and plain text output formats
- [x] Tracked metrics:
  - [x] `received` - HTTP requests received
  - [x] `emsSends` - Messages sent to EMS
  - [x] `emsReplies` - Reply messages received
  - [x] `returnMessage` - Successful responses
  - [x] `errors` - Error count
  - [x] `processed` - Successfully processed requests

### Phase 6: Logging & Debug ✅

- [x] Java Util Logging (JUL) integration
- [x] Command-line log level configuration (`DEBUG=<level>`)
- [x] Per-request debug logging (`DEBUG: YES` header)
- [x] Comprehensive debug messages throughout codebase
- [x] Timestamp on all log entries

### Phase 7: Startup & Configuration ✅

- [x] Command-line port configuration
- [x] Startup banner with compile date/time
- [x] Example command line display at startup
- [x] Valid log levels documentation

### Phase 8: Documentation ✅

- [x] `README.md` - Quick start guide
- [x] `documentation.md` - Complete API reference
- [x] `LICENSE` - MIT License
- [x] `.gitignore` - Standard Java/Maven ignores
- [x] Code comments and Javadoc

---

## Future Enhancements

### Phase 9: Security (Planned)

- [ ] SSL/TLS support for HTTPS
- [ ] Basic authentication for HTTP endpoints
- [ ] EMS SSL connection support
- [ ] API key validation
- [ ] Rate limiting

### Phase 10: High Availability (Planned)

- [ ] EMS connection pooling
- [ ] Automatic reconnection on failure
- [ ] Health check endpoint (`/health`)
- [ ] Graceful shutdown handling
- [ ] Multiple EMS server failover

### Phase 11: Advanced Messaging (Planned)

- [ ] Topic support (publish/subscribe)
- [ ] Durable subscribers
- [ ] Message selectors
- [ ] Batch message sending
- [ ] BytesMessage support (binary payloads)
- [ ] MapMessage support

### Phase 12: Operations (Planned)

- [ ] Prometheus metrics export
- [ ] Docker containerization
- [ ] Kubernetes deployment manifests
- [ ] Configuration file support (YAML/properties)
- [ ] Environment variable configuration

### Phase 13: Testing (Planned)

- [ ] Unit tests with JUnit
- [ ] Integration tests with embedded EMS
- [ ] Performance benchmarks
- [ ] Load testing scripts

---

## Technical Debt

| Item | Priority | Description |
|------|----------|-------------|
| Connection pooling | High | Create persistent EMS connections instead of per-request |
| Error handling | Medium | More granular error codes and messages |
| Request validation | Medium | Schema validation for JSON payloads |
| Async processing | Low | Non-blocking request handling |

---

## Efficiency Optimization Summary

| Optimization | Impact | Effort | Status |
|--------------|--------|--------|--------|
| **Connection pooling** | High | Medium | Done |
| **Factory caching** | High | Low | Done |
| **Hostname caching** | Medium | Low | Done |
| **Compute inferContentType once** | Low | Low | Pending |
| **Fixed thread pool** | Medium | Low | Done |
| **Header normalization cache** | Low | Low | Pending |
| **Content-Length pre-allocation** | Low | Low | Pending |
| **Destination caching** | Low | Low | Pending |

**Implemented:**
- `EmsConnectionPool` – caches `TibjmsConnectionFactory` per (serverUrl, user, password) and pools Connections (max 10 per endpoint, 5 idle).
- `HostnameCache` – resolves hostname once at class load; used by HttpServerApp, EmsJmsService, and EmsReplyListener for default correlation ID and reply location.
- Fixed thread pool – HttpServer uses `newFixedThreadPool(max(16, processors*4))` instead of cached pool to avoid thread creation overhead.

---

## Architecture Decisions

### ADR-001: JDK 1.8 (Java 8) Default
**Decision:** Target JDK 1.8 (Java 8) as the default and minimum version  
**Rationale:** Broad enterprise compatibility, LTS support, widely deployed  
**Consequence:** Cannot use `Set.of()`, `Map.of()` (Java 9+)

### ADR-002: Built-in HTTP Server
**Decision:** Use `com.sun.net.httpserver.HttpServer`  
**Rationale:** No external dependencies, lightweight, sufficient for use case  
**Consequence:** Limited HTTP/2 support, basic features only

### ADR-003: Header Key Normalization
**Decision:** Normalize all header keys to first-char-uppercase, rest-lowercase  
**Rationale:** Java's HashMap is case-sensitive; HTTP headers are case-insensitive  
**Consequence:** All lookups must use normalized keys

### ADR-004: Correlation ID Generation
**Decision:** Use `hostname-UUID` format for auto-generated correlation IDs  
**Rationale:** Enables tracing across distributed systems  
**Consequence:** Slightly longer correlation IDs

---

## Release History

| Version | Date | Changes |
|---------|------|---------|
| 1.0-SNAPSHOT | 2026-01 | Initial development |

---

## Contributors

- Development Team

---

## References

- [TIBCO EMS Documentation](https://docs.tibco.com/products/tibco-enterprise-message-service)
- [JMS 2.0 Specification](https://jcp.org/en/jsr/detail?id=343)
- [Java Util Logging](https://docs.oracle.com/javase/8/docs/api/java/util/logging/package-summary.html)
