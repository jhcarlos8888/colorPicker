#!/usr/bin/env bash
# Builds both Debian packages of ColorPicker into build/dist:
#   colorpicker_<version>-1_<arch>.deb    self-contained (bundled Java runtime)
#   colorpicker-light_<version>_all.deb   small, uses the Java of the system
# Extra arguments go to Gradle, e.g.: packaging/build-deb.sh -PpackagingJdk=25
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

missing=()
for tool in java dpkg-deb fakeroot; do
    command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
done
if ((${#missing[@]} > 0)); then
    echo "Faltan herramientas: ${missing[*]}" >&2
    echo "Instálalas con: sudo apt install openjdk-17-jdk dpkg-dev fakeroot" >&2
    exit 1
fi

java_major="$(java -version 2>&1 | sed -n 's/.* version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
if [[ -z "$java_major" || "$java_major" -lt 17 ]]; then
    echo "Se necesita un JDK 17 o superior (java -version indica: ${java_major:-desconocido})." >&2
    echo "Instálalo con: sudo apt install openjdk-17-jdk" >&2
    exit 1
fi

./gradlew clean test jpackageDeb debLight "$@"

shopt -s nullglob
debs=(build/dist/*.deb)
if ((${#debs[@]} == 0)); then
    echo "No se generó ningún paquete .deb." >&2
    exit 1
fi

echo
echo "Paquetes generados:"
for deb in "${debs[@]}"; do
    printf '  %s (%s)\n' "$project_dir/$deb" "$(du -h "$deb" | cut -f1)"
done
echo
echo "Para instalar (elige uno de los dos; no pueden estar instalados a la vez):"
for deb in "${debs[@]}"; do
    echo "  sudo apt install \"$project_dir/$deb\""
done
