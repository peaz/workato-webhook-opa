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


conf/config.yml
```
extensions:
  webhook:
    controllerClass: com.workato.proxy.webhook.WebhookOPAExtension
```