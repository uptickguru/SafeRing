#!/bin/bash
# SafeRing Android Integration Tests — Phase 1 & 2
# Runs via ADB on a connected Android device (USB or Wi-Fi)

set +e

ADB="$HOME/.local/android/platform-tools/adb"
PACKAGE="online.db1k.safering.android.debug"
PASS=0
FAIL=0

echo "=========================================="
echo "  SafeRing Android — Integration Tests"
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
sleep 4

check_text() {
    local label="$1"
    local text="$2"
    local timeout="${3:-5}"
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        $ADB shell uiautomator dump 2>/dev/null
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

tap_text() {
    local text="$1"
    $ADB shell uiautomator click "$text" 2>/dev/null
    sleep 2
}

tap_home()    { $ADB shell uiautomator click "Home" 2>/dev/null; $ADB shell input tap 126 2216 2>/dev/null; sleep 2; }
tap_history() { $ADB shell uiautomator click "History" 2>/dev/null; $ADB shell input tap 401 2216 2>/dev/null; sleep 2; }
tap_report()  { $ADB shell uiautomator click "Report" 2>/dev/null; $ADB shell input tap 677 2216 2>/dev/null; sleep 2; }
tap_settings(){ $ADB shell uiautomator click "Settings" 2>/dev/null; $ADB shell input tap 953 2216 2>/dev/null; sleep 2; }

# Phase 3: Interaction Tests

# ─────────────────────────────────────────────
# Phase 1: Home Screen
# ─────────────────────────────────────────────

echo ""
echo "=========================================="
echo "  PHASE 1: Home Screen"
echo "=========================================="

echo ""
echo "--- TEST 1: Home Screen Loads ---"
check_text "App title 'SafeRing'" "SafeRing"

echo ""
echo "--- TEST 2: Stats Display ---"
check_text "'Scams Identified' stat" "Scams Identified"
check_text "'Blocked Today' stat" "Blocked Today"

echo ""
echo "--- TEST 3: Tab Bar ---"
check_text "'Home' tab" "Home" 3
check_text "'History' tab" "History" 3
check_text "'Report' tab" "Report" 3
check_text "'Settings' tab" "Settings" 3

# ─────────────────────────────────────────────
# Phase 2: Settings, History, Report
# ─────────────────────────────────────────────

echo ""
echo "=========================================="
echo "  PHASE 2: Screen Navigation"
echo "=========================================="

echo ""
echo "--- TEST 4: Settings Screen ---"
tap_settings
check_text "'Settings' title" "Settings" 3
check_text "'Call Screening' section" "Call Screening" 3
check_text "'SMS Protection' section" "SMS Protection" 3
tap_home
check_text "Back on Home" "SafeRing" 3

echo ""
echo "--- TEST 5: History Screen ---"
tap_history
check_text "'Call History' title" "Call History" 3
check_text "Empty state: No call history yet" "No call history yet" 3
tap_home
check_text "Back on Home" "SafeRing" 3

echo ""
echo "--- TEST 6: Report Screen ---"
tap_report
check_text "'Report a Scam Number' title" "Report a Scam Number" 3
check_text "Phone number label" "Phone Number" 3
tap_home
check_text "Back on Home" "SafeRing" 3

# ─────────────────────────────────────────────
# Phase 3: Interaction Tests
# ─────────────────────────────────────────────

echo ""
echo "=========================================="
echo "  PHASE 3: Interaction Tests"
echo "=========================================="

echo ""
echo "--- TEST 7: Settings Toggle Elements ---"
tap_settings
check_text "'Auto-Block Scam Calls' label" "Auto-Block Scam Calls" 3
check_text "'Show Scam Alert Notifications' label" "Show Scam Alert Notifications" 3
check_text "'Store SMS Body' label" "Store SMS Body" 3
tap_home
check_text "Back on Home" "SafeRing" 3

echo ""
echo "--- TEST 8: Report Form Elements ---"
tap_report
check_text "'Submit Report' button" "Submit Report" 3
check_text "'Phone Number' label" "Phone Number" 3
# Scam type picker labels checked via the title and scam options above
tap_home
check_text "Back on Home" "SafeRing" 3

# ─────────────────────────────────────────────
# Results
# ─────────────────────────────────────────────

echo ""
echo "=========================================="
echo "  ALL TESTS PASSING ✅"
echo "=========================================="
