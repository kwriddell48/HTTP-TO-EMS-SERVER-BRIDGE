# Python Client Plan — HTTP to EMS Bridge

## Overview

Create a Python program that acts as a client to the HTTP to EMS Bridge. The Python program will send HTTP requests to the bridge (running on `http://localhost:8080` or configured URL), which forwards messages to TIBCO EMS via JMS.

## Architecture

```
┌─────────────────────┐      HTTP GET/POST       ┌─────────────────────┐      JMS       ┌─────────────┐
│  Python Program     │ ───────────────────────► │  HTTP to EMS Bridge │ ─────────────► │ TIBCO EMS   │
│  (this plan)        │                          │  (Java, port 8080)  │                │             │
└─────────────────────┘ ◄─────────────────────── └─────────────────────┘                └─────────────┘
        │                              HTTP Response
        │
        └── Can be: CLI tool, library, script, or service
```

## Requirements

### Python Version
- **Python 3.8+** (or 3.6+ for broader compatibility)
- Use `requests` library for HTTP (or built-in `urllib`)

### Dependencies
- `requests` — HTTP client
- Optional: `argparse` or `click` for CLI
- Optional: `pydantic` for request validation
- Optional: `python-dotenv` for configuration

## Plan Phases

### Phase 1: Core Client Library
- [ ] Create `http_to_ems_client.py` module
- [ ] Implement `send_message()` — publish-only mode
- [ ] Implement `send_request_reply()` — request-reply with timeout
- [ ] Support all required headers: `JMS-URL`, `JMS-USR`, `JMS-QU1`
- [ ] Support optional headers: `JMS-PSW`, `JMS-QU2`, `JMS-TIMEOUT`, `JMS-PUBLISH-ONLY`, `JMS-CORRELATION-ID`
- [ ] Support optional JMS properties as headers: `JMSPriority`, `JMSType`, `JMS_TIBCO_COMPRESS`, etc.
- [ ] Handle JSON and plain-text responses
- [ ] Error handling for HTTP 4xx/5xx responses
- [ ] Configurable bridge URL (default: `http://localhost:8080`)

### Phase 2: CLI Tool
- [ ] Create `ems_send.py` or `http_ems_cli.py` — command-line interface
- [ ] Arguments: `--url`, `--user`, `--queue`, `--body`, `--publish-only`, `--reply-queue`, `--timeout`
- [ ] Environment variable support for credentials
- [ ] Read message body from stdin or `--file`
- [ ] Output response (message ID or reply body)

### Phase 3: Convenience Scripts
- [ ] `publish.py` — simple publish-only wrapper
- [ ] `request_reply.py` — simple request-reply wrapper
- [ ] `get_stats.py` — fetch metrics from `/metrics` or `/stats`

### Phase 4: Configuration & Packaging
- [ ] Support config file (YAML/JSON/INI) for bridge URL, defaults
- [ ] `requirements.txt` for dependencies
- [ ] Optional: `setup.py` or `pyproject.toml` for pip install
- [ ] Optional: Docker image that includes Python client + bridge

### Phase 5: Advanced Features
- [ ] Async client using `aiohttp` (optional)
- [ ] Retry logic with exponential backoff
- [ ] Connection pooling / session reuse
- [ ] Batch send multiple messages

## API Design (Python Client)

### Proposed Interface

```python
from http_to_ems_client import HttpToEmsClient

client = HttpToEmsClient(base_url="http://localhost:8080")

# Publish-only
result = client.send(
    jms_url="tcp://ems-server:7222",
    user="admin",
    password="secret",
    queue="queue.orders",
    body='{"orderId": 123}',
    publish_only=True
)
print(result.message_id)

# Request-reply
reply = client.send(
    jms_url="tcp://ems-server:7222",
    user="admin",
    queue="queue.request",
    reply_queue="queue.reply",
    body="get status",
    timeout_ms=30000
)
print(reply.body)
```

## File Structure

```
java-http-to-ems/
├── Python/
│   ├── http_to_ems_client.py    # Core client library
│   ├── ems_send.py              # CLI tool
│   ├── publish.py               # Publish-only script
│   ├── request_reply.py         # Request-reply script
│   ├── get_stats.py             # Metrics script
│   ├── requirements.txt
│   └── README.md              # Python client usage
```

## Usage Examples (Planned)

```bash
# Publish-only
python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --body "Hello" --publish-only

# Request-reply
python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.req --reply-queue queue.reply --body "ping"

# Get metrics
python get_stats.py
# or: curl http://localhost:8080/metrics
```

## Risks & Considerations

| Item | Mitigation |
|------|------------|
| Bridge must be running | Document dependency; optional health check |
| Timeouts | Configurable; handle 504 gracefully |
| Large payloads | Consider streaming; document size limits |
| Credentials in CLI | Use env vars; avoid logging passwords |

## References

- [HTTP to EMS Bridge documentation](documentation.md)
- [Bridge API reference](documentation.md#api-reference)
- [Python requests library](https://requests.readthedocs.io/)
- [TIBCO EMS](https://docs.tibco.com/products/tibco-enterprise-message-service)
