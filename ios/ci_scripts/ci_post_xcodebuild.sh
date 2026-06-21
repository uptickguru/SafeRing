#!/bin/sh

# SafeRing — Xcode Cloud post-build script
# Runs after the build completes.

set -e

echo "SafeRing: Build complete. Archive path: $CI_ARCHIVE_PATH"

# Upload dSYMs for crash reporting if desired
# if [ -d "$CI_ARCHIVE_PATH/dSYMs" ]; then
#   echo "Uploading dSYMs..."
# fi
