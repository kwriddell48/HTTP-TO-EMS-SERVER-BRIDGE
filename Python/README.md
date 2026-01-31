# Python Client — HTTP to EMS Bridge

Python client for the HTTP to EMS Bridge. Sends messages to TIBCO EMS via the Java bridge.

## Requirements

- **Python 3.6+**
- **requests** library
- HTTP to EMS Bridge running (default: http://localhost:8080)

## Installation

```bash
cd Python
pip install -r requirements.txt
```

## Usage

### Core Client Library

```python
from http_to_ems_client import HttpToEmsClient, BridgeError

client = HttpToEmsClient(base_url="http://localhost:8080")

# Publish-only
result = client.publish(
    jms_url="tcp://localhost:7222",
    user="admin",
    password="secret",
    queue="queue.test",
    body='{"orderId": 123}'
)
print(result.message_id)

# Request-reply
result = client.request_reply(
    jms_url="tcp://localhost:7222",
    user="admin",
    queue="queue.request",
    reply_queue="queue.reply",
    body="ping",
    timeout_ms=30000
)
print(result.body)

# Get metrics
stats = client.get_stats()
print(stats)
```

### CLI Tool (ems_send.py)

```bash
# Publish-only
python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --body "Hello" --publish-only

# Request-reply
python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.req --reply-queue queue.reply --body "ping"

# Read body from file
python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --file message.json --publish-only

# Read body from stdin
echo "Hello" | python ems_send.py --url tcp://localhost:7222 --user admin --queue queue.test --publish-only

# Custom bridge URL
python ems_send.py --bridge http://localhost:9000 --url tcp://ems:7222 --user admin --queue queue.test --body "Hi" --publish-only
```

### Convenience Scripts

```bash
# Publish-only
python publish.py --url tcp://localhost:7222 --user admin --queue queue.test --body "Hello"

# Request-reply
python request_reply.py --url tcp://localhost:7222 --user admin --queue queue.req --reply-queue queue.reply --body "ping"

# Get metrics
python get_stats.py
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `HTTP_EMS_BRIDGE_URL` | Bridge URL (default: http://localhost:8080) |
| `JMS_USR` | EMS username |
| `JMS_PSW` | EMS password |

## Files

| File | Description |
|------|-------------|
| `http_to_ems_client.py` | Core client library |
| `ems_send.py` | CLI tool |
| `publish.py` | Publish-only wrapper |
| `request_reply.py` | Request-reply wrapper |
| `get_stats.py` | Metrics script |
| `requirements.txt` | Dependencies |

## See Also

- [HTTP to EMS Bridge documentation](../documentation.md)
- [Python client plan](../python-client-plan.md)
