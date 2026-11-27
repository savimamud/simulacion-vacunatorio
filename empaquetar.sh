#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
NAME="vacunatorio-java-tp5-grupo8"
ZIP="$ROOT/${NAME}.zip"

if [ -z "$JAVA_HOME" ] && [ -d "/opt/homebrew/opt/openjdk@17" ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

chmod +x "$ROOT/compilar.sh" "$ROOT/ejecutar.sh" "$ROOT/backend/mvnw" 2>/dev/null || true
"$ROOT/compilar.sh"

STAGE="$ROOT/dist-package/$NAME"
rm -rf "$ROOT/dist-package"
mkdir -p "$STAGE"

rsync -a \
  --exclude 'backend/target/classes' \
  --exclude 'backend/target/maven-archiver' \
  --exclude 'backend/target/maven-status' \
  --exclude 'backend/target/generated-sources' \
  --exclude 'frontend/node_modules' \
  --exclude 'dist-package' \
  --exclude '.DS_Store' \
  --exclude '*.zip' \
  "$ROOT/backend" "$ROOT/frontend" \
  "$ROOT/compilar.sh" "$ROOT/ejecutar.sh" "$ROOT/compilar.bat" "$ROOT/ejecutar.bat" \
  "$ROOT/README.md" "$ROOT/ENTREGA.md" \
  "$STAGE/"

cp "$ROOT/backend/target/vacunatorio.jar" "$STAGE/backend/target/"
chmod +x "$STAGE/compilar.sh" "$STAGE/ejecutar.sh" "$STAGE/backend/mvnw" 2>/dev/null || true

cd "$ROOT/dist-package"
rm -f "$ZIP"
zip -r "$ZIP" "$NAME" -x "*.DS_Store"
rm -rf "$ROOT/dist-package"

echo "✓ ZIP: $ZIP"
