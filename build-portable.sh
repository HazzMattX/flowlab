#!/bin/bash
# build-portable.sh — DRAFT, not yet the active build script.
#
# Purpose: a portable replacement for build.sh (Flow v0.1, versionCode 1),
# for self-compile on a MacBook. The original build.sh is untouched.
# Nothing here is applied until it is reviewed and approved for swapping in.
#
# Differences from build.sh:
#  - No hardcoded paths: SDK / JDK / kotlinc come from environment variables,
#    with auto-detection for macOS (Homebrew Temurin + kotlin, ~/Library/Android/sdk)
#    and for this VM's layout as a fallback. The project dir is the script's
#    own directory, so the repo can live anywhere.
#  - No hardcoded keystore password: the password comes only from
#    FLOW_KEYSTORE_PASS. The script fails fast if it is unset.
#    (Rotating the password itself is a separate step, not done here —
#    see Flow-v0.1-Code-Review.md item 1.)
#  - No auto-creation of a keystore: if flow.keystore is missing, the script
#    stops and says so, instead of silently creating a different signing key.
#    Copy the real keystore in to build an update that installs over v0.1.
#  - `--check` prints the resolved paths and verifies the tools exist,
#    without building anything.
#
# Usage (MacBook, Homebrew):
#   brew install --cask temurin@17
#   brew install kotlin
#   # SDK: Android Studio, or cmdline-tools into ~/Library/Android/sdk
#   export FLOW_KEYSTORE_PASS='...'   # your keystore password
#   cp flow.keystore ~/workspace/flowlab/   # same-key route only
#   ./build-portable.sh --check        # verify paths first
#   ./build-portable.sh                # builds build/Flow-v0.1.apk
#
# Overrides (all optional):
#   FLOW_JAVA_HOME, FLOW_ANDROID_SDK (or ANDROID_HOME / ANDROID_SDK_ROOT),
#   FLOW_KOTLIN_HOME, FLOW_KOTLIN_STDLIB, FLOW_KEYSTORE, FLOW_COPY_TO
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJECT_DIR/src/main/java"
RES_DIR="$PROJECT_DIR/src/main/res"
MANIFEST="$PROJECT_DIR/src/main/AndroidManifest.xml"
OUT="$PROJECT_DIR/build"

# --- Resolve JAVA_HOME -------------------------------------------------------
if [ -n "${FLOW_JAVA_HOME:-}" ]; then
  JAVA_HOME="$FLOW_JAVA_HOME"
elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
if [ -z "${JAVA_HOME:-}" ]; then
  for cand in "$HOME/workspace/tools/jdk-17.0.21+9" \
              /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
              "$HOME/.sdkman/candidates/java/current"; do
    [ -x "$cand/bin/java" ] && JAVA_HOME="$cand" && break
  done
fi
[ -n "${JAVA_HOME:-}" ] || { echo "Could not find JDK 17. Set FLOW_JAVA_HOME." >&2; exit 1; }

# --- Resolve Android SDK -----------------------------------------------------
SDK="${FLOW_ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
if [ -z "$SDK" ]; then
  for cand in "$HOME/Library/Android/sdk" "$HOME/workspace/android-sdk" \
              "$HOME/Android/Sdk" /opt/android-sdk /usr/lib/android-sdk; do
    [ -d "$cand/build-tools" ] && SDK="$cand" && break
  done
fi
[ -n "$SDK" ] || { echo "Could not find Android SDK. Set FLOW_ANDROID_SDK (or ANDROID_HOME)." >&2; exit 1; }
BT="$SDK/build-tools/34.0.0"
[ -x "$BT/aapt2" ] || { echo "build-tools 34.0.0 not found under $SDK. Install it with sdkmanager." >&2; exit 1; }

# --- Resolve kotlinc + kotlin-stdlib -----------------------------------------
KOTLIN_HOME="${FLOW_KOTLIN_HOME:-}"
if [ -z "$KOTLIN_HOME" ]; then
  for cand in /opt/homebrew/opt/kotlin/libexec /usr/local/opt/kotlin/libexec \
              "$HOME/workspace/tools/kotlinc"; do
    [ -x "$cand/bin/kotlinc" ] && KOTLIN_HOME="$cand" && break
  done
fi
if [ -z "$KOTLIN_HOME" ] && command -v kotlinc >/dev/null 2>&1; then
  KOTLIN_BIN="$(command -v kotlinc)"
  # kotlinc is usually $KOTLIN_HOME/bin/kotlinc, or a brew shim one level up from libexec
  if [ -f "$(dirname "$KOTLIN_BIN")/../lib/kotlin-stdlib.jar" ]; then
    KOTLIN_HOME="$(cd "$(dirname "$KOTLIN_BIN")/.." && pwd)"
  fi
fi
[ -n "$KOTLIN_HOME" ] || { echo "Could not find kotlinc. Set FLOW_KOTLIN_HOME (brew install kotlin)." >&2; exit 1; }
STDLIB="${FLOW_KOTLIN_STDLIB:-$KOTLIN_HOME/lib/kotlin-stdlib.jar}"
[ -f "$STDLIB" ] || { echo "kotlin-stdlib.jar not found at $STDLIB. Set FLOW_KOTLIN_STDLIB." >&2; exit 1; }

# --- Keystore ----------------------------------------------------------------
KEYSTORE="${FLOW_KEYSTORE:-$PROJECT_DIR/flow.keystore}"
if [ "${1:-}" = "--check" ]; then
  echo "PROJECT_DIR=$PROJECT_DIR"
  echo "JAVA_HOME=$JAVA_HOME"
  echo "SDK=$SDK"
  echo "KOTLIN_HOME=$KOTLIN_HOME"
  echo "STDLIB=$STDLIB"
  echo "KEYSTORE=$KEYSTORE"
  [ -f "$KEYSTORE" ] && echo "keystore: present" || echo "keystore: MISSING (copy it in for the same-key route)"
  [ -n "${FLOW_KEYSTORE_PASS:-}" ] && echo "FLOW_KEYSTORE_PASS: set" || echo "FLOW_KEYSTORE_PASS: NOT set (required to build)"
  "$JAVA_HOME/bin/java" -version 2>&1 | head -1
  PATH="$JAVA_HOME/bin:$PATH" "$KOTLIN_HOME/bin/kotlinc" -version 2>&1 | head -1 || true
  exit 0
fi

: "${FLOW_KEYSTORE_PASS:?Set FLOW_KEYSTORE_PASS to your keystore password before building.}"
[ -f "$KEYSTORE" ] || {
  echo "No keystore at $KEYSTORE." >&2
  echo "Copy your flow.keystore here to build an update that installs over v0.1," >&2
  echo "or build unsigned / with your own key — this script will not create a new key for you." >&2
  exit 1
}

export PATH="$JAVA_HOME/bin:$BT:$KOTLIN_HOME/bin:$PATH"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/res" "$OUT/gen" "$OUT/rclasses"
rm -rf "$OUT/classes"/* "$OUT/dex"/* "$OUT/gen"/* "$OUT/rclasses"/*

# Compile resources, then link manifest + resources
"$BT/aapt2" compile --dir "$RES_DIR" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/app-base.apk" -I "$SDK/platforms/android-34/android.jar" \
  --manifest "$MANIFEST" -R "$OUT/res.zip" --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 0.1 \
  --auto-add-overlay

# Compile R.java first, then Kotlin against android.jar + R classes
"$JAVA_HOME/bin/javac" -cp "$SDK/platforms/android-34/android.jar" -d "$OUT/rclasses" \
  $(find "$OUT/gen" -name "R.java")
kotlinc -classpath "$SDK/platforms/android-34/android.jar:$OUT/rclasses" -d "$OUT/classes" \
  $(find "$SRC" -name "*.kt")

# Dex, including kotlin-stdlib so lambdas / stdlib calls work on device
"$BT/d8" --output "$OUT/dex" --lib "$SDK/platforms/android-34/android.jar" \
  $(find "$OUT/classes" -name "*.class") $(find "$OUT/rclasses" -name "*.class") "$STDLIB"

# Add dex to apk
cd "$OUT/dex" && zip -q -r "$OUT/app-base.apk" classes*.dex && cd "$PROJECT_DIR"

# Align and sign with the existing keystore (same key = installs over v0.1)
"$BT/zipalign" -f 4 "$OUT/app-base.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$FLOW_KEYSTORE_PASS" \
  --key-pass "pass:$FLOW_KEYSTORE_PASS" --out "$OUT/Flow-v0.1.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" verify --verbose "$OUT/Flow-v0.1.apk"
ls -lh "$OUT/Flow-v0.1.apk"
"$BT/aapt2" dump badging "$OUT/Flow-v0.1.apk" | head -30

if [ -n "${FLOW_COPY_TO:-}" ]; then
  cp "$OUT/Flow-v0.1.apk" "$FLOW_COPY_TO"
  echo "Copied to $FLOW_COPY_TO"
fi
