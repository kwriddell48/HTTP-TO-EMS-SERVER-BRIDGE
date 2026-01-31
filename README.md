# HTTP to EMS Messager (bridge)

A lightweight Java HTTP server that bridges HTTP requests to TIBCO Enterprise Message Service (EMS) via JMS.

## Quick Start

```bash
# Build
mvn clean package

# Run (Windows)
java -cp "target/classes;lib/tibjms.jar;lib/javax.jms-api-2.0.1.jar" http.to.ems.messager.Main 8080

# Run (Linux/macOS)
java -cp "target/classes:lib/tibjms.jar:lib/javax.jms-api-2.0.1.jar" http.to.ems.messager.Main 8080

# Test
curl -X POST http://localhost:8080/ \
  -H "JMS-URL: tcp://localhost:7222" \
  -H "JMS-USR: admin" \
  -H "JMS-QU1: queue.test" \
  -H "JMS-PUBLISH-ONLY: YES" \
  -d "Hello EMS"
```

## Features

- HTTP GET/POST to JMS messaging
- Publish-only and request-reply patterns
- JMS property passthrough via HTTP headers
- Real-time metrics endpoint
- Configurable debug logging
- Built with JDK 1.8 (Java 8) - Default

## EmsReplyListener

Reply worker that listens to a request queue and sends replies to JMSReplyTo with timestamps and location info:

```bash
java -cp "target/classes;lib/*" http.to.ems.messager.EmsReplyListener tcp://localhost:7222 admin password queue.request
```

## Python Client

A Python client is available in the `Python/` folder. See [Python/README.md](Python/README.md).

```bash
cd Python && pip install -r requirements.txt
python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --body "Hello" --publish-only
```

## Documentation

See [documentation.md](documentation.md) for complete API reference, usage examples, and configuration options.

## Requirements

- **JDK 1.8** (Java 8) - Default and recommended
- Maven 3.x (for building)

### Required JAR Files

| JAR File | Description | Source |
|----------|-------------|--------|
| `tibjms.jar` | TIBCO EMS client library | TIBCO EMS installation (`<EMS_HOME>/lib/`) |
| `javax.jms-api-2.0.1.jar` | JMS 2.0 API | [Maven Central](https://repo1.maven.org/maven2/javax/jms/javax.jms-api/2.0.1/) |

Place these JAR files in a `lib/` directory or update the classpath accordingly.

## License

MIT License
