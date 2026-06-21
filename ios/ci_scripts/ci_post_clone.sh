#!/bin/sh

# SafeRing — Xcode Cloud post-clone script
# Runs after the repo is cloned, before the build starts.

set -e

echo "SafeRing: Running post-clone setup..."

# Install any dependencies (if using SPM, Xcode Cloud handles it automatically)
# If you need CocoaPods, uncomment:
# pod install

echo "SafeRing: Post-clone setup complete."
