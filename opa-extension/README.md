# Workato Webhook Proxy

A simple HTTP reverse proxy service that forwards requests to Workato webhook URLs.

## Overview

This service acts as an intermediary that receives HTTP requests and forwards them to Workato platform webhooks. It's built with Spring Boot and Java 21.

## Features

- ✅ HTTP POST and GET request proxying
- ✅ Header forwarding
- ✅ JSON request/response handling
- ✅ Health check endpoint
- ✅ Comprehensive logging

## Quick Start

### Prerequisites

- Java 21 (LTS)
- Maven 3.9+

### Build

```bash
cd extension
mvn clean package
```

## API Endpoints

### Health Check

```bash
GET /health
```

Response:
```json
{
  "status": "healthy",
  "service": "workato-webhook-proxy"
}
```

### Proxy POST Request

```bash
POST /proxy?webhook_url=<WORKATO_WEBHOOK_URL>
Content-Type: application/json

{
  "key": "value",
  "data": "your payload"
}
```

Or include webhook_url in the body:

```bash
POST /proxy
Content-Type: application/json

{
  "webhook_url": "https://www.workato.com/webhooks/...",
  "key": "value",
  "data": "your payload"
}
```

### Proxy GET Request

```bash
GET /proxy?webhook_url=<WORKATO_WEBHOOK_URL>&param1=value1&param2=value2
```

## Response Format

Success response:
```json
{
  "success": true,
  "webhook_response": {
    // Response from Workato webhook
  }
}
```

Error response:
```json
{
  "success": false,
  "error": "Error description",
  "message": "Detailed error message"
}
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Server port
server.port=8080

# Logging level
logging.level.com.workato=DEBUG

# Max request size
spring.codec.max-in-memory-size=10MB
```

## Examples

### Using curl

```bash
# Health check
curl http://localhost:8080/health

# Forward POST request
curl -X POST http://localhost:8080/proxy \
  -H "Content-Type: application/json" \
  -d '{
    "webhook_url": "https://www.workato.com/webhooks/rest/YOUR_TOKEN/trigger",
    "event": "user_created",
    "user_id": 12345,
    "email": "user@example.com"
  }'

# Forward GET request
curl "http://localhost:8080/proxy?webhook_url=https://www.workato.com/webhooks/rest/YOUR_TOKEN/trigger&event=test&user_id=123"
```

### Using JavaScript

```javascript
const response = await fetch('http://localhost:8080/proxy', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    webhook_url: 'https://www.workato.com/webhooks/rest/YOUR_TOKEN/trigger',
    event: 'order_created',
    order_id: 67890,
    amount: 99.99
  })
});

const result = await response.json();
console.log(result);
```

## Architecture

```
Client → HTTP Request → Proxy Service → Workato Webhook
                            ↓
                      Forward headers
                      Forward body
                      Handle response
```

## Development

### Project Structure

```
extension/
├── src/main/java/com/workato/extension/solace/
│   ├── ExtensionController.java      # Main REST controller
│   └── service/
│       └── WebhookProxyService.java  # Proxy logic
├── src/main/resources/
│   └── application.properties        # Configuration
└── pom.xml                           # Maven dependencies
```

### Key Dependencies

- Spring Boot 3.4.2
- Spring Web (REST endpoints)
- Spring WebFlux (HTTP client)
- Jackson (JSON processing)

## Troubleshooting

### Connection Timeout

Default timeout is 30 seconds. Adjust in `WebhookProxyService.java`:

```java
private static final int TIMEOUT_SECONDS = 30;
```

### Memory Issues

Increase max request size in `application.properties`:

```properties
spring.codec.max-in-memory-size=20MB
```

## License

Copyright © Workato
