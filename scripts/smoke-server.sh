#!/usr/bin/env bash
# Boots the dedicated dev server and fails on startup errors or a crash report.
# The server starts with eula=true in the ignored run/ folder; nothing is
# installed into the real game directory.
set -u
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jdk-17.0.20.1+1}"
mkdir -p run build
printf 'eula=true\n' > run/eula.txt

LOG=build/smoke-server.log
STAMP=build/smoke.stamp
: > "$LOG"
touch "$STAMP"

# runServer has no console input in this setup, so a fixed window is the
# simplest reliable smoke test; the server is up in seconds once caches exist.
timeout 120 ./gradlew runServer --offline --console=plain > "$LOG" 2>&1 || true

if ! grep -q 'Done (' "$LOG"; then
    echo "Server did not finish starting. Last lines:"
    tail -40 "$LOG"
    exit 1
fi

if grep -E '\[Server thread/ERROR\]|Uncaught exception' "$LOG"; then
    echo "Server reported errors during startup."
    exit 1
fi

CRASH=$(find run/crash-reports -name '*.txt' -newer "$STAMP" 2>/dev/null | head -1)
if [ -n "$CRASH" ]; then
    echo "Crash report written: $CRASH"
    exit 1
fi

echo "Smoke test passed: $(grep -m1 'Done (' "$LOG")"
