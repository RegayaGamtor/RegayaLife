#!/bin/sh
# Build: butuh JDK 21 + Maven + koneksi internet (untuk mengunduh paper-api).
set -e
cd "$(dirname "$0")"
mvn -B clean package
echo
echo "Selesai: target/HeartLifesteal-1.0.0.jar"
jar tf target/HeartLifesteal-1.0.0.jar | grep -E "plugin.yml|config.yml|messages.yml|recipe.yml|HeartLifestealPlugin.class"
