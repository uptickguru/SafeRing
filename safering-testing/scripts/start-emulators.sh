#!/bin/bash
# Start iOS Simulator + Android Emulator for testing
# Usage: ./scripts/start-emulators.sh [ios|android|both]

set -e

MODE="${1:-both}"

start_ios() {
  echo "📱 Starting iOS Simulator..."
  # Boot the default iPhone simulator
  xcrun simctl boot "$(xcrun simctl list devices available | grep "iPhone" | head -1 | awk -F'[()]' '{print $2}')" 2>/dev/null || true
  open -a Simulator
  echo "✅ iOS Simulator booted"
}

start_android() {
  echo "🤖 Starting Android Emulator..."
  # Find an AVD if any
  AVD=$(emulator -list-avds 2>/dev/null | head -1)
  if [ -n "$AVD" ]; then
    echo "Starting AVD: $AVD"
    emulator -avd "$AVD" -no-snapshot-load &
    echo "✅ Android Emulator booted"
  else
    echo "⚠️  No Android AVD found. Create one with: avdmanager create avd -n pixel8 -k 'system-images;android-35;google_apis;x86_64'"
    echo "   Or run: flutter emulators --launch pixel_8"
  fi
}

case "$MODE" in
  ios)    start_ios ;;
  android) start_android ;;
  both)   start_ios; sleep 5; start_android ;;
  *)      echo "Usage: $0 [ios|android|both]"; exit 1 ;;
esac

echo ""
echo "🎯 Emulators ready! Run tests with:"
echo "   maestro test flows/"
echo "   maestro test test-suites/smoke.yaml"
echo ""
echo "📞 To inject a test call:"
echo "   ADB:  adb emu gsm call 5551234567"
echo "   iOS:  xcrun simctl openurl booted 'tel:15551234567'"
echo "📱 To inject a test SMS:"
echo "   ADB:  adb emu sms send 5551234567 'Test message'"
echo "   iOS:  (via push notification)"
