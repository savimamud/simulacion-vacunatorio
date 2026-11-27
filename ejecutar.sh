#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ ! -f "$ROOT/backend/target/vacunatorio.jar" ]; then
  echo "Primero ejecutá: ./compilar.sh"
  exit 1
fi

if [ ! -f "$ROOT/frontend/dist/index.html" ]; then
  echo "Frontend no compilado. Ejecutá: ./compilar.sh"
  exit 1
fi

if [ -z "$JAVA_HOME" ] && [ -d "/opt/homebrew/opt/openjdk@17" ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

export PORT="${PORT:-8080}"
export FRONTEND_DIST="$ROOT/frontend/dist"

echo "▶ Backend + Frontend → http://127.0.0.1:$PORT"
cd "$ROOT/backend"
java -jar target/vacunatorio.jar
