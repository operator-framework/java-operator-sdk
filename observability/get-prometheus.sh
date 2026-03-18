#!/bin/bash
#
# Copyright Java Operator SDK Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Downloads Prometheus locally if not already present.
# Prints the path to the prometheus binary on stdout.
# Usage: ./get-prometheus.sh [install-dir]

set -e

PROMETHEUS_VERSION="3.4.0"
INSTALL_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.prometheus}"

# If already downloaded, just print the path
if [ -x "$INSTALL_DIR/prometheus" ]; then
  echo "$INSTALL_DIR/prometheus"
  exit 0
fi

# Check if prometheus is already on PATH
if command -v prometheus &> /dev/null; then
  echo "prometheus"
  exit 0
fi

# Detect OS and architecture
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

case "$OS" in
  linux)  PLATFORM="linux" ;;
  darwin) PLATFORM="darwin" ;;
  *)      echo "Unsupported OS: $OS" >&2; exit 1 ;;
esac

case "$ARCH" in
  x86_64|amd64)   CPU_ARCH="amd64" ;;
  aarch64|arm64)   CPU_ARCH="arm64" ;;
  *)               echo "Unsupported architecture: $ARCH" >&2; exit 1 ;;
esac

TAR_NAME="prometheus-${PROMETHEUS_VERSION}.${PLATFORM}-${CPU_ARCH}"
DOWNLOAD_URL="https://github.com/prometheus/prometheus/releases/download/v${PROMETHEUS_VERSION}/${TAR_NAME}.tar.gz"

mkdir -p "$INSTALL_DIR"
TAR_FILE="$INSTALL_DIR/prometheus.tar.gz"

echo "Downloading Prometheus v${PROMETHEUS_VERSION} for ${PLATFORM}/${CPU_ARCH}..." >&2
curl -fsSL -o "$TAR_FILE" "$DOWNLOAD_URL"

echo "Extracting..." >&2
tar xzf "$TAR_FILE" -C "$INSTALL_DIR"

# Move the binary to the install dir root for a clean path
mv "$INSTALL_DIR/$TAR_NAME/prometheus" "$INSTALL_DIR/prometheus"
mv "$INSTALL_DIR/$TAR_NAME/promtool" "$INSTALL_DIR/promtool" 2>/dev/null || true

# Clean up
rm -rf "$INSTALL_DIR/$TAR_NAME" "$TAR_FILE"

echo "Prometheus installed to $INSTALL_DIR/prometheus" >&2
echo "$INSTALL_DIR/prometheus"
