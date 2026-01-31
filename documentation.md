# HTTP to EMS Messager

A Java HTTP server that accepts HTTP GET/POST requests and forwards messages to TIBCO EMS via JMS.

[![Java](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Building](#building)
- [Running](#running)
- [Command Line Arguments](#command-line-arguments)
- [API Reference](#api-reference)
  - [Endpoints](#endpoints)
  - [HTTP Headers](#http-headers)
  - [Optional JMS Properties](#optional-jms-properties)
- [Usage Examples](#usage-examples)
- [Metrics](#metrics)
- [Logging](#logging)
- [Architecture](#architecture)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Overview

**HTTP to EMS Messager** is a lightweight Java application that bridges HTTP and TIBCO Enterprise Message Service (EMS). It allows clients to send messages to JMS queues via simple HTTP requests, supporting both:

- **Publish-only mode**: Send a message and receive the JMS Message ID
- **Request-reply mode**: Send a message and wait for a reply from a response queue

---

## Features

- **HTTP GET/POST support** - Both methods read the message body from the HTTP request body
- **Publish-only messaging** - Fire-and-forget with JMS Message ID response
- **Request-reply messaging** - Synchronous request/response pattern with configurable timeout
- **JMS property passthrough** - Forward custom JMS properties via HTTP headers
- **TIBCO EMS integration** - Native support for TIBCO-specific properties
- **Metrics endpoint** - Real-time statistics in JSON or plain text
- **Debug logging** - Configurable log levels via command line
- **JDK 1.8 (Java 8)** - Default build target, runs on Java 8 and later
- **CORS enabled** - Access-Control-Allow-Origin: * header included

---

## Requirements

- **JDK 1.8** (Java 8) - Default and recommended
- **Maven 3.x** (for building)

### Required JAR Files

| JAR File | Description | Source |
|----------|-------------|--------|
| `tibjms.jar` | TIBCO EMS client library | TIBCO EMS installation (`<EMS_HOME>/lib/`) |
| `javax.jms-api-2.0.1.jar` | JMS 2.0 API | [Maven Central](https://repo1.maven.org/maven2/javax/jms/javax.jms-api/2.0.1/) |

---

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/java-http-to-ems.git
cd java-http-to-ems
```

### 2. Install Required JAR Files

Create a `lib/` directory and place the required JAR files:

```bash
mkdir lib

# Copy TIBCO EMS client JAR
# Windows: copy from TIBCO EMS installation
copy "C:\tibco\ems\8.6\lib\tibjms.jar" lib\

# Linux/macOS
cp /opt/tibco/ems/8.6/lib/tibjms.jar lib/

# Download JMS API JAR from Maven Central
# Windows (PowerShell)
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/javax/jms/javax.jms-api/2.0.1/javax.jms-api-2.0.1.jar" -OutFile "lib\javax.jms-api-2.0.1.jar"

# Linux/macOS
curl -o lib/javax.jms-api-2.0.1.jar https://repo1.maven.org/maven2/javax/jms/javax.jms-api/2.0.1/javax.jms-api-2.0.1.jar
```

### Alternative: Configure pom.xml

Update `pom.xml` with your JAR paths:

```xml
<dependency>
    <groupId>com.tibco</groupId>
    <artifactId>tibjms</artifactId>
    <version>8.6</version>
    <scope>system</scope>
    <systemPath>C:\JAVA-JARS\tibjms.jar</systemPath>
</dependency>
```

Or install to local Maven repository:

```bash
mvn install:install-file \
  -Dfile=/path/to/tibjms.jar \
  -DgroupId=com.tibco \
  -DartifactId=tibjms \
  -Dversion=8.6 \
  -Dpackaging=jar
```

---

## Building

### Using Maven

```bash
mvn clean package
```

This creates `target/java-http-to-ems-1.0-SNAPSHOT.jar`.

### Manual Compilation

```bash
# Create output directory
mkdir -p target/classes

# Compile (adjust classpath as needed)
javac -cp "path/to/tibjms.jar;path/to/javax.jms-api-2.0.1.jar" \
  -d target/classes \
  src/main/java/http/to/ems/messager/*.java
```

---

## Running

### Using Maven

```bash
mvn exec:java
```

### Using Java directly

```bash
# Windows
java -cp "target/classes;lib/tibjms.jar;lib/javax.jms-api-2.0.1.jar" http.to.ems.messager.Main 8080

# Linux/macOS
java -cp "target/classes:lib/tibjms.jar:lib/javax.jms-api-2.0.1.jar" http.to.ems.messager.Main 8080
```

### Using JAR file

```bash
# Windows
java -cp "target/java-http-to-ems-1.0-SNAPSHOT.jar;lib/tibjms.jar;lib/javax.jms-api-2.0.1.jar" ^
  http.to.ems.messager.Main 8080

# Linux/macOS
java -cp "target/java-http-to-ems-1.0-SNAPSHOT.jar:lib/tibjms.jar:lib/javax.jms-api-2.0.1.jar" \
  http.to.ems.messager.Main 8080
```

### EmsReplyListener — Reply Queue Worker

Listens to a **request queue**, receives messages, generates a reply with timestamps and location info, and sends it to the message's **JMSReplyTo** destination (the reply queue).

**Usage:**
```bash
java -cp "target/classes;lib/tibjms.jar;lib/javax.jms-api-2.0.1.jar" \
  http.to.ems.messager.EmsReplyListener <ems-url> <user> <password> <request-queue>
```

**Example:**
```bash
java -cp "target/classes;lib/tibjms.jar;lib/javax.jms-api-2.0.1.jar" \
  http.to.ems.messager.EmsReplyListener tcp://localhost:7222 admin password queue.request
```

**With Maven:**
```bash
mvn exec:java -Dexec.mainClass=http.to.ems.messager.EmsReplyListener \
  -Dexec.args="tcp://localhost:7222 admin password queue.request"
```

**Flow:**
1. HTTP Bridge sends request to `queue.request` (JMS-QU1) with JMSReplyTo = `queue.reply` (JMS-QU2)
2. EmsReplyListener consumes from `queue.request`
3. For each message, builds a JSON reply with: `timestamp`, `receivedAt`, `hostname`, `location`, `javaVersion`, `userName`, `messageNumber`, `requestMessageId`, `correlationId`, `originalMessage`, `replyFrom`
4. Sends reply to JMSReplyTo with JMSCorrelationID preserved
5. HTTP Bridge's consumer receives reply on `queue.reply` and returns it to the HTTP client

---

## Command Line Arguments

| Argument | Description | Default |
|----------|-------------|---------|
| `[port]` | HTTP server port number | `8080` |
| `DEBUG=<level>` | Set logging level | `INFO` |

### Startup Banner

On startup, the application displays:

```
=======================================================
  HTTP to EMS Messager
  Compile Date/Time: 2026-01-30 14:15:30
  Log Level: INFO
=======================================================

Example command line:
  java -cp <classpath> http.to.ems.messager.Main [port] [DEBUG=level]

Examples:
  java -cp target/classes;lib/* http.to.ems.messager.Main 8080
  java -cp target/classes;lib/* http.to.ems.messager.Main 8080 DEBUG=FINE
  java -cp target/classes;lib/* http.to.ems.messager.Main DEBUG=FINEST

Valid DEBUG levels: OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL
=======================================================

HTTP server listening on port 8080
```

### Debug Levels

| Level | Description |
|-------|-------------|
| `OFF` | No logging |
| `SEVERE` | Serious failures only |
| `WARNING` | Warnings and above |
| `INFO` | Informational messages (default) |
| `CONFIG` | Configuration messages |
| `FINE` | Debug-level tracing |
| `FINER` | More detailed tracing |
| `FINEST` | Most detailed tracing |
| `ALL` | All messages |

---

## API Reference

### Endpoints

| Endpoint | Methods | Description |
|----------|---------|-------------|
| `/` | GET, POST | Send message to EMS |
| `/api` | GET, POST | Send message to EMS (alias) |
| `/metrics` | GET | Get server metrics |
| `/stats` | GET | Get server metrics (alias) |

### HTTP Headers

#### Required Headers

| Header | Description | Example |
|--------|-------------|---------|
| `JMS-URL` | TIBCO EMS server URL | `tcp://localhost:7222` |
| `JMS-USR` | EMS username | `admin` |
| `JMS-QU1` | Destination queue name | `queue.request` |

#### Optional Headers

| Header | Description | Default | Example |
|--------|-------------|---------|---------|
| `JMS-PSW` | EMS password | (empty) | `password123` |
| `JMS-QU2` | Reply queue for request-reply | Temporary queue | `queue.reply` |
| `JMS-PUBLISH-ONLY` | Publish without waiting for reply | `NO` | `YES` |
| `JMS-TIMEOUT` | Reply timeout in milliseconds | `30000` | `60000` |
| `JMS-CORRELATION-ID` | JMS correlation ID | `hostname-UUID` | `my-corr-123` |
| `STATISTICS` | Return statistics only (skip EMS) | `NO` | `YES` |
| `DEBUG` | Enable debug logging for this request | `NO` | `YES` |
| `Content-Type` | Request content type | `text/plain` | `application/json` |
| `Accept` | Preferred response type | `text/plain` | `application/json` |

### Optional JMS Properties

These HTTP headers are passed through to the JMS message as properties:

| Header | JMS Property | Valid Values | Limits |
|--------|--------------|--------------|--------|
| `JMSDeliveryMode` | DeliveryMode | `PERSISTENT`, `NON_PERSISTENT`, `1`, `2` | - |
| `JMSType` | JMSType | String | Max 255 chars |
| `JMSExpiration` | Expiration | Milliseconds (≥0) | 0 = no expire |
| `JMSPriority` | Priority | `0`-`9` | 0=lowest, 9=highest |
| `JMSDeliveryTime` | Delivery delay | Milliseconds (≥0) | - |
| `JMS_TIBCO_COMPRESS` | Compression | `true`, `false` | - |
| `JMS_TIBCO_PRESERVE_UNDELIVERED` | Preserve undelivered | `true`, `false` | - |
| `JMSXGroupID` | Group ID | String | Max 4096 bytes |
| `JMSXGroupSeq` | Group sequence | Integer | - |

#### TIBCO EMS Limits

| Property | Limit |
|----------|-------|
| Correlation ID | Max 4096 bytes (4 KB) |
| Property name | Max 256 characters |
| Property value | Max 4096 bytes |
| JMSType | Max 255 characters |
| JMSPriority | 0-9 |

---

## Usage Examples

### Publish-Only (Fire and Forget)

Send a message without waiting for a reply:

```bash
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-PSW: password" \
  -H "JMS-QU1: queue.orders" \
  -H "JMS-PUBLISH-ONLY: YES" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"orderId": 12345, "product": "Widget"}'
```

Response (JSON):
```json
{"messageId":"ID:EMS-SERVER.123456789.1"}
```

### Request-Reply

Send a message and wait for a reply:

```bash
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.request" \
  -H "JMS-QU2: queue.reply" \
  -H "JMS-TIMEOUT: 60000" \
  -H "Content-Type: application/json" \
  -d '{"action": "getStatus", "id": 999}'
```

Response:
```json
{"status": "OK", "data": {...}}
```

### Request-Reply with Temporary Queue

Omit `JMS-QU2` to use a temporary reply queue:

```bash
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.request" \
  -H "JMS-TIMEOUT: 30000" \
  -d "Hello EMS"
```

### Using GET Method

```bash
curl -X GET http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.test" \
  -H "JMS-PUBLISH-ONLY: YES" \
  -d "Message body here"
```

### Setting JMS Priority

```bash
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.priority" \
  -H "JMS-PUBLISH-ONLY: YES" \
  -H "JMSPriority: 9" \
  -d "High priority message"
```

### With TIBCO Compression

```bash
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.data" \
  -H "JMS-PUBLISH-ONLY: YES" \
  -H "JMS_TIBCO_COMPRESS: true" \
  -d "Large message content..."
```

### Get Statistics Only

```bash
curl http://localhost:8080/ \
  -H "STATISTICS: YES" \
  -H "Accept: application/json"
```

Response:
```json
{"received":150,"emsSends":145,"emsReplies":100,"returnMessage":145,"errors":5,"processed":145}
```

### Debug a Request

```bash
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.test" \
  -H "DEBUG: YES" \
  -d "Test message"
```

---

## Metrics

Access metrics via `/metrics` or `/stats`:

```bash
# JSON format
curl http://localhost:8080/metrics -H "Accept: application/json"

# Plain text format
curl http://localhost:8080/metrics
```

### Metrics Fields

| Field | Description |
|-------|-------------|
| `received` | Total HTTP requests received |
| `emsSends` | Messages sent to EMS |
| `emsReplies` | Reply messages received from EMS |
| `returnMessage` | Successful responses returned |
| `errors` | Total errors encountered |
| `processed` | Successfully processed requests |

---

## Logging

### Log Levels

Set via command line:

```bash
java -cp ... http.to.ems.messager.Main 8080 DEBUG=FINE
```

### Log Format

All log messages include timestamp and context:

```
[2026-01-30T14:15:30.123Z] handle POST /api
[2026-01-30T14:15:30.125Z] Connecting to EMS
[2026-01-30T14:15:30.200Z] Connected, session created, destQu1=queue.request
[2026-01-30T14:15:30.205Z] sendRequestReply sending request to JMS-QU1
[2026-01-30T14:15:30.250Z] Reply received (listener)
[2026-01-30T14:15:30.251Z] sendResponse status=200 contentType=application/json bodyBytes=45
```

### Per-Request Debug

Enable debug for a single request using the `DEBUG: YES` header.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      HTTP Client                             │
│                  (curl, Postman, etc.)                       │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP GET/POST
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     HttpServerApp                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ / endpoint  │  │ /api        │  │ /metrics, /stats    │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
│         │                │                     │             │
│         └────────────────┴─────────────────────┘             │
│                          │                                   │
│  ┌───────────────────────┴───────────────────────────────┐  │
│  │                   MessageMetrics                       │  │
│  │  (received, emsSends, emsReplies, errors, processed)  │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      EmsJmsService                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ • Connect to TIBCO EMS                               │    │
│  │ • Create JMS Session                                 │    │
│  │ • Set JMS properties from HTTP headers               │    │
│  │ • Send to JMS-QU1 queue                              │    │
│  │ • Wait for reply on JMS-QU2 (if request-reply)       │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │ JMS/TIBCO EMS Protocol
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    TIBCO EMS Server                          │
│  ┌───────────┐  ┌───────────┐  ┌───────────────────────┐    │
│  │ JMS-QU1   │  │ JMS-QU2   │  │ Temporary Queues      │    │
│  │ (request) │  │ (reply)   │  │ (auto-created)        │    │
│  └───────────┘  └───────────┘  └───────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### Source Files

| File | Description |
|------|-------------|
| `Main.java` | Entry point, argument parsing, logging setup |
| `HttpServerApp.java` | HTTP server, request handling, header validation |
| `EmsJmsService.java` | TIBCO EMS connection, JMS messaging |
| `MessageMetrics.java` | Thread-safe counters for statistics |

---

## Troubleshooting

### Common Errors

#### 400 Bad Request: Missing headers

Ensure required headers are present:

```bash
curl -v http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.test"
```

#### 503 Service Unavailable: EMS connection failed

- Verify EMS server is running
- Check URL, username, password
- Ensure network connectivity

```bash
# Test EMS connectivity
telnet localhost 7222
```

#### 504 Gateway Timeout: Timeout waiting for reply

- Increase `JMS-TIMEOUT` value
- Verify reply queue has a consumer
- Check if the backend service is processing messages

#### ClassNotFoundException: com.tibco.tibjms.TibjmsConnectionFactory

- Add `tibjms.jar` to classpath
- Add `javax.jms-api-2.0.1.jar` to classpath
- Verify both JAR file paths are correct
- Ensure classpath separator is correct (`;` for Windows, `:` for Linux/macOS)

### Debug Mode

Enable detailed logging:

```bash
java -cp ... http.to.ems.messager.Main 8080 DEBUG=FINEST
```

Or per-request:

```bash
curl -H "DEBUG: YES" ...
```

### Testing EMS Connectivity

```bash
# Simple publish test
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.test" \
  -H "JMS-PUBLISH-ONLY: YES" \
  -d "Test"
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## Support

For issues and questions:
- Open a GitHub issue
- Check existing documentation
- Enable debug logging for detailed diagnostics
