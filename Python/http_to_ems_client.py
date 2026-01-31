"""
HTTP to EMS Bridge - Python Client Library

Sends HTTP requests to the HTTP to EMS Bridge, which forwards messages to TIBCO EMS via JMS.
Requires the Java bridge to be running (default: http://localhost:8080).
"""

import json
from typing import Any, Dict, Optional

try:
    import requests
except ImportError:
    raise ImportError("Please install requests: pip install requests")


DEFAULT_BRIDGE_URL = "http://localhost:8080"


class HttpToEmsClient:
    """
    Client for the HTTP to EMS Bridge.
    """

    def __init__(self, base_url: str = DEFAULT_BRIDGE_URL, timeout: float = 60.0):
        """
        Initialize the client.

        Args:
            base_url: Bridge URL (e.g., http://localhost:8080)
            timeout: Request timeout in seconds
        """
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self._session = requests.Session()

    def send(
        self,
        jms_url: str,
        user: str,
        queue: str,
        body: str = "",
        password: Optional[str] = None,
        reply_queue: Optional[str] = None,
        publish_only: bool = False,
        timeout_ms: Optional[int] = None,
        correlation_id: Optional[str] = None,
        headers: Optional[Dict[str, str]] = None,
        use_json: bool = True,
    ) -> "SendResult":
        """
        Send a message to EMS via the bridge.

        Args:
            jms_url: EMS server URL (e.g., tcp://localhost:7222)
            user: EMS username
            queue: Destination queue (JMS-QU1)
            body: Message body
            password: EMS password (optional)
            reply_queue: Reply queue for request-reply (JMS-QU2)
            publish_only: If True, fire-and-forget; if False, wait for reply
            timeout_ms: Reply timeout in milliseconds (default: 30000)
            correlation_id: JMS correlation ID (optional)
            headers: Additional HTTP headers (e.g., JMSPriority, JMSType)
            use_json: If True, use application/json for request/response

        Returns:
            SendResult with status, body, message_id (publish-only), or reply body
        """
        req_headers = {
            "JMS-URL": jms_url,
            "JMS-USR": user,
            "JMS-QU1": queue,
        }
        if password:
            req_headers["JMS-PSW"] = password
        if publish_only:
            req_headers["JMS-PUBLISH-ONLY"] = "YES"
        else:
            if reply_queue:
                req_headers["JMS-QU2"] = reply_queue
            if timeout_ms is not None:
                req_headers["JMS-TIMEOUT"] = str(timeout_ms)
        if correlation_id:
            req_headers["JMS-CORRELATION-ID"] = correlation_id

        if use_json:
            req_headers["Content-Type"] = "application/json"
            req_headers["Accept"] = "application/json"

        if headers:
            req_headers.update(headers)

        url = f"{self.base_url}/"  # or /api
        resp = self._session.post(
            url,
            data=body,
            headers=req_headers,
            timeout=self.timeout,
        )

        content_type = resp.headers.get("Content-Type", "text/plain")
        is_json = "application/json" in content_type
        response_body = resp.text

        if not resp.ok:
            if is_json:
                try:
                    err = json.loads(response_body)
                    error_msg = err.get("error", response_body)
                except json.JSONDecodeError:
                    error_msg = response_body
            else:
                error_msg = response_body
            raise BridgeError(resp.status_code, error_msg)

        if publish_only and is_json:
            try:
                data = json.loads(response_body)
                message_id = data.get("messageId", "")
                return SendResult(status=resp.status_code, body=response_body, message_id=message_id)
            except json.JSONDecodeError:
                return SendResult(status=resp.status_code, body=response_body)

        return SendResult(status=resp.status_code, body=response_body)

    def publish(
        self,
        jms_url: str,
        user: str,
        queue: str,
        body: str = "",
        password: Optional[str] = None,
        **kwargs: Any,
    ) -> "SendResult":
        """Publish-only: send message and get JMS Message ID."""
        return self.send(
            jms_url=jms_url,
            user=user,
            queue=queue,
            body=body,
            password=password,
            publish_only=True,
            **kwargs,
        )

    def request_reply(
        self,
        jms_url: str,
        user: str,
        queue: str,
        body: str = "",
        password: Optional[str] = None,
        reply_queue: Optional[str] = None,
        timeout_ms: Optional[int] = None,
        **kwargs: Any,
    ) -> "SendResult":
        """Request-reply: send message and wait for reply."""
        return self.send(
            jms_url=jms_url,
            user=user,
            queue=queue,
            body=body,
            password=password,
            reply_queue=reply_queue,
            publish_only=False,
            timeout_ms=timeout_ms or 30000,
            **kwargs,
        )

    def get_stats(self, use_json: bool = True) -> Dict[str, Any]:
        """Get bridge metrics from /metrics."""
        headers = {}
        if use_json:
            headers["Accept"] = "application/json"
        resp = self._session.get(
            f"{self.base_url}/metrics",
            headers=headers or None,
            timeout=self.timeout,
        )
        resp.raise_for_status()
        if use_json and "application/json" in resp.headers.get("Content-Type", ""):
            return resp.json()
        return {"raw": resp.text}

    def close(self) -> None:
        """Close the session."""
        self._session.close()

    def __enter__(self) -> "HttpToEmsClient":
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()


class SendResult:
    """Result of a send operation."""

    def __init__(
        self,
        status: int,
        body: str,
        message_id: Optional[str] = None,
    ):
        self.status = status
        self.body = body
        self.message_id = message_id or ""

    def __repr__(self) -> str:
        return f"SendResult(status={self.status}, message_id={self.message_id!r}, body_len={len(self.body)})"


class BridgeError(Exception):
    """Error from the HTTP to EMS Bridge."""

    def __init__(self, status_code: int, message: str):
        self.status_code = status_code
        self.message = message
        super().__init__(f"Bridge error {status_code}: {message}")
