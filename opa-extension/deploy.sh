#!/bin/bash

set -e

# Configuration
OPA_EXTENSION_DIR="/Users/ken/workato-dev/workato-webhook-opa/opa-extension"
WORKATO_BIN_DIR="/Users/ken/workato-dev/workato-agent-macos-amd64/bin"
LIB_EXT_DIR="/Users/ken/workato-dev/workato-agent-macos-amd64/lib_ext"
RUN_SCRIPT="${WORKATO_BIN_DIR}/run.sh"

# Use Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

echo "=========================================="
echo "Starting Workato OPA Extension Deployment"
echo "=========================================="
echo ""

# Step 1: Build the JAR
echo "[1/4] Building JAR..."
cd "$OPA_EXTENSION_DIR"
./gradlew clean jar
JAR_FILE="$OPA_EXTENSION_DIR/build/libs/opa-extension-0.2.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found at $JAR_FILE"
    exit 1
fi
echo "✓ JAR built successfully: $JAR_FILE"
echo ""

# Step 2: Stop the Workato OPA process
echo "[2/4] Stopping Workato OPA process..."
PID=$(pgrep -f "java.*workato.*agent" || true)
if [ -n "$PID" ]; then
    echo "Found process PID: $PID"
    kill $PID
    sleep 2
    # Verify it's stopped
    if pgrep -f "java.*workato.*agent" > /dev/null; then
        echo "Process still running, force killing..."
        kill -9 $PID
        sleep 1
    fi
    echo "✓ Workato OPA process stopped"
else
    echo "⚠ No Workato OPA process found running"
fi
echo ""

# Step 3: Copy JAR to lib_ext
echo "[3/4] Copying JAR to lib_ext..."
mkdir -p "$LIB_EXT_DIR"
cp "$JAR_FILE" "$LIB_EXT_DIR/opa-extension-0.2.jar"
echo "✓ JAR copied to $LIB_EXT_DIR"
echo ""

# Step 4: Restart the Workato OPA process
echo "[4/4] Starting Workato OPA process..."
cd "$WORKATO_BIN_DIR"
nohup bash "$RUN_SCRIPT" > /tmp/workato-opa.log 2>&1 &
OPA_PID=$!
echo "✓ Workato OPA process started with PID: $OPA_PID"
echo ""

# Wait a bit and verify it's running
sleep 3
if pgrep -f "java.*workato.*agent" > /dev/null; then
    echo "=========================================="
    echo "✓ Deployment completed successfully!"
    echo "=========================================="
    echo "Process is running. Check logs at:"
    echo "  /tmp/workato-opa.log"
else
    echo "=========================================="
    echo "⚠ WARNING: Process may not have started"
    echo "=========================================="
    echo "Check logs at:"
    echo "  /tmp/workato-opa.log"
    exit 1
fi
