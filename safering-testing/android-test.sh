#!/bin/bash
# Phase 1 Android Integration Tests

set +e  # Don't exit on failure - we want to see all results

ADB="$HOME/.local/android/platform-tools/adb"
PACKAGE="online.db1k.safering.android.debug"
PASS=0
FAIL=0

echo "=========================================="
echo "  SafeRing Android — Phase 1 Tests"
echo "=========================================="

# Clear app state
echo ""
echo "=== Clearing app state ==="
$ADB shell pm clear "$PACKAGE" > /dev/null 2>&1
echo "  Done"

# Launch app
echo ""
echo "=== Launching app ==="
$ADB shell am start -n "$PACKAGE/online.db1k.safering.android.MainActivity" > /dev/null 2>&1
sleep 5

check_text() {
    local label="$1"
    local text="$2"
    local timeout="${3:-5}"
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        $ADB shell uiautomator dump /dev/null 2>/dev/null
        local result
        result=$($ADB shell cat /sdcard/window_dump.xml 2>/dev/null | grep -c "text=\"$text\"" )
        if [ "$result" -gt 0 ]; then
            echo "  ✓ $label"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    echo "  ✗ $label (not found in ${timeout}s)"
    return 1
}

echo ""
echo "--- TEST 1: Home Screen Loads ---"
check_text "App title 'SafeRing'" "SafeRing"
if [ $? -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi

echo ""
echo "--- TEST 2: Stats Display ---"
check_text "'Scams Identified' stat" "Scams Identified"
if [ $? -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi
check_text "'Blocked Today' stat" "Blocked Today"
if [ $? -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi

echo ""
echo "--- TEST 3: Tab Bar ---"
check_text "'Home' tab" "Home" 3
if [ $? -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi
check_text "'History' tab" "History" 3
if [ $? -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi
check_text "'Report' tab" "Report" 3
if [ $? -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi
check_text "'Settings' tab" "Settings" 3
if [ $? -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi

echo ""
echo "=========================================="
echo "  RESULTS: $PASS passed, $FAIL failed"
if [ $FAIL -eq 0 ]; then
    echo "  ANDROID PHASE 1: ALL TESTS PASSED ✅"
else
    echo "  SOME TESTS FAILED ❌"
fi
echo "=========================================="
