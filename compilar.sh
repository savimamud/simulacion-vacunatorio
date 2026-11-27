#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$JAVA_HOME" ] && [ -d "/opt/homebrew/opt/openjdk@17" ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "══════════════════════════════════════════"
echo "  Compilando Vacunatorio"
echo "══════════════════════════════════════════"

echo "[1/2] Frontend..."
cd "$ROOT/frontend"
if ! command -v npm >/dev/null 2>&1; then
  echo "✗ Node.js/npm requerido para compilar el frontend"
  exit 1
fi
npm install --silent 2>/dev/null || npm install
npm run build
echo "      ✓ frontend/dist listo"

echo "[2/2] Backend (Java)..."
mkdir -p "$ROOT/backend/src/main/resources/public"
rm -rf "$ROOT/backend/src/main/resources/public"/*
cp -r "$ROOT/frontend/dist/"* "$ROOT/backend/src/main/resources/public/"

cd "$ROOT/backend"
MVN="./mvnw"
command -v mvn >/dev/null 2>&1 && MVN="mvn"
$MVN -q clean package -DskipTests
echo "      ✓ backend/target/vacunatorio.jar"

echo ""
echo "Listo. Ejecutá: ./ejecutar.sh"
echo "══════════════════════════════════════════"
