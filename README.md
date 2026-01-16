# Workato Webhook Proxy

A simple HTTP reverse proxy service that forwards requests to Workato webhook URLs.

## Overview

This service acts as an intermediary that receives HTTP requests and forwards them to Workato platform webhooks. It's built with Spring Boot and Java 21.

## Features

- ✅ HTTP POST and GET request proxying
- ✅ Header forwarding
- ✅ Health check endpoint
- ✅ Comprehensive logging

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.9+ or Gradle 8.x

### Build

Using Maven:
```bash
cd opa-extension
mvn clean package
```

Using Gradle:
```bash
cd opa-extension
./gradlew clean build
```

## Installation

### 1. Deploy the Extension JAR

After building, copy the JAR file to your Workato OPA's extension library directory:

Using Maven:
```bash
cp opa-extension/target/opa-extension-1.0-SNAPSHOT.jar <OPA_HOME>/lib_ext/
```

Using Gradle:
```bash
cp opa-extension/build/libs/opa-extension-1.0-SNAPSHOT.jar <OPA_HOME>/lib_ext/
```

Replace `<OPA_HOME>` with your Workato OPA installation directory.

### 2. Configure the Extension

Edit your OPA's `conf/config.yml` to register the webhook extension:

```yaml
extensions:
  webhook:
    controllerClass: com.workato.proxy.webhook.WebhookOPAExtension
```

### 3. Restart the OPA

Restart your Workato OPA service to load the extension:

```bash
cd <OPA_HOME>
./bin/stop.sh
./bin/start.sh
```

### 4. Create a Connection in Workato

In your Workato workspace:

1. Navigate to **Connections** and click **Create**
2. Fill in the connection details:
   - **Connection name**: Choose a descriptive name (e.g., "Test Webhook")
   - **Connection type**: Select your OPA group (e.g., "Work MBP")
   - **Profile**: Enter `webhook`
   - **Connection ID**: Enter a unique identifier (e.g., `dev`)
   - **Listen port**: Enter the port for the HTTP listener (e.g., `8080`)
3. Click **Connect**

![Webhook Connection Setup](resource/connection.jpg)

### 5. Create a Custom Connector

The project includes a custom connector definition for Workato. Follow these steps to set it up:

1. **Copy the connector file** to your Workato SDK directory:
   ```bash
   cp workato-sdk/connector.rb <WORKATO_SDK_HOME>/lib/workato/connector_sdk/
   ```

2. **In your Workato workspace**, navigate to **Tools -> Connector SDK** and click **New connector**

3. **Configure the connector**:
   - **Choose a starting point**: Select "Get guided from a Workato template"
   - **Add connector details**: Choose a descriptive name (e.g., "Webhook Proxy Connector") and (optionally) add a logo if you wish.
   - **Connector code**: Copy and paste the contents of `workato-sdk/connector.rb`
   - **Save** and **release latest version** to your workspace.

4. **Test the connector** by creating a recipe that uses it

### 6. Test the Connection

Test the webhook endpoint using curl:

```bash
curl -X POST http://localhost:8080/<connection-id> \
  -H "Content-Type: application/json" \
  -d '{
    "timestamp": 1737025800,
    "order_id": "ORD-234",
    "customer_email": "customer@example.com",
    "amount": 99.99
  }'
```

Replace `<connection-id>` with the Connection ID you specified (e.g., `dev`).

## Project Structure

```
.
├── opa-extension/           # Java Spring Boot OPA Extension
│   ├── src/
│   ├── pom.xml             # Maven configuration
│   └── build.gradle        # Gradle configuration
├── workato-sdk/
│   └── connector.rb        # Workato Custom Connector Definition
├── README.md               # This file
└── LICENSE                 # MIT License
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

**Disclaimer**: Users are responsible for their own use of this software. The authors and contributors are not liable for any damages or issues arising from the use of this software.
