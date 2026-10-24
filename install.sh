#!/usr/bin/env bash
# Linux/macOS: качает JDK 25, Gradle, Fabric installer, собирает мод, кладёт в mods. Fabric API — вручную.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"; TOOLS="$ROOT/tools"; MCVER=26.2
mkdir -p "$TOOLS"
OS=linux; MC="$HOME/.minecraft"
if [ "$(uname)" = "Darwin" ]; then OS=mac; MC="$HOME/Library/Application Support/minecraft"; fi
ARCH=x64; case "$(uname -m)" in arm64|aarch64) ARCH=aarch64;; esac

if ! ls "$TOOLS"/jdk-25* >/dev/null 2>&1; then
  curl -L "https://api.adoptium.net/v3/binary/latest/25/ga/$OS/$ARCH/jdk/hotspot/normal/eclipse" -o "$TOOLS/jdk.tgz"
  tar -xzf "$TOOLS/jdk.tgz" -C "$TOOLS"; rm "$TOOLS/jdk.tgz"
fi
JDK="$(ls -d "$TOOLS"/jdk-25* | head -1)"
[ "$OS" = mac ] && JDK="$JDK/Contents/Home"
export JAVA_HOME="$JDK"; export PATH="$JDK/bin:$PATH"

if [ ! -d "$TOOLS/gradle-9.5.1" ]; then
  curl -L https://services.gradle.org/distributions/gradle-9.5.1-bin.zip -o "$TOOLS/gradle.zip"
  unzip -q "$TOOLS/gradle.zip" -d "$TOOLS"; rm "$TOOLS/gradle.zip"
fi
cd "$ROOT"; "$TOOLS/gradle-9.5.1/bin/gradle" build --no-daemon
JAR="$(ls build/libs/moody_god-*.jar | grep -v sources | head -1)"

URL="$(curl -s https://meta.fabricmc.net/v2/versions/installer | grep -o '"url":"[^"]*"' | head -1 | cut -d'"' -f4)"
curl -L "$URL" -o "$TOOLS/fabric-installer.jar"
java -jar "$TOOLS/fabric-installer.jar" client -mcversion $MCVER -dir "$MC"

mkdir -p "$MC/mods"; cp "$JAR" "$MC/mods/"
echo; echo "ГОТОВО. Теперь вручную скачай Fabric API для $MCVER (modrinth.com/mod/fabric-api) в $MC/mods"
