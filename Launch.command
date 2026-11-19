#!/bin/sh
set -eu
cd -- "$(dirname -- "$0")"
if ! command -v java >/dev/null 2>&1; then
  echo "HFML requires Java 21 or newer. Install a trusted JDK and retry."
  exit 1
fi
exec java -jar HFML.jar
