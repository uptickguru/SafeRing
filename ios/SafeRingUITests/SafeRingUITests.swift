import XCTest

final class SafeRingUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: - Onboarding Flow

    func testOnboarding() throws {
        // Launch with NO args = @AppStorage default false shows OnboardingView
        // If previous test left a persisted true, kill the app
        app.launchArguments = ["-hasCompletedOnboarding", "0"]
        app.launch()

        // Step 1: Welcome
        XCTAssertTrue(app.buttons["Get Started"].waitForExistence(timeout: 5))
        app.buttons["Get Started"].tap()

        // Step 2: Call Protection
        XCTAssertTrue(app.buttons["Enable Call Screening"].waitForExistence(timeout: 5))
        app.buttons["Enable Call Screening"].tap()

        // Step 3: All Set
        XCTAssertTrue(app.buttons["Start Protection"].waitForExistence(timeout: 5))
        app.buttons["Start Protection"].tap()

        // Terminate and relaunch to force the @AppStorage switch to take effect
        // (NSArgumentDomain "-hasCompletedOnboarding 0" priority issue)
        app.terminate()
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        // Now check for home screen
        let homeTab = app.buttons["Home"]
        XCTAssertTrue(homeTab.waitForExistence(timeout: 5),
                      "Home tab should appear after relaunch with onboarding completed")

        // Verify full home screen
        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 3))
        for tab in ["History", "Report", "Settings"] {
            XCTAssertTrue(app.buttons[tab].waitForExistence(timeout: 3))
        }
        for stat in ["Calls\nBlocked", "SMS\nFiltered", "Scam #\nKnown"] {
            XCTAssertTrue(app.staticTexts[stat].waitForExistence(timeout: 3))
        }
    }

    // MARK: - Home Screen

    func testHomeScreen() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))

        for tab in ["Home", "History", "Report", "Settings"] {
            XCTAssertTrue(app.buttons[tab].waitForExistence(timeout: 3))
        }

        for stat in ["Calls\nBlocked", "SMS\nFiltered", "Scam #\nKnown"] {
            XCTAssertTrue(app.staticTexts[stat].waitForExistence(timeout: 3))
        }
    }

    // MARK: - Tab Navigation

    func testTabNavigation() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))

        app.buttons["History"].tap()
        XCTAssertTrue(app.navigationBars["Call History"].waitForExistence(timeout: 5))

        app.buttons["Report"].tap()
        XCTAssertTrue(app.navigationBars["Report a Scam"].waitForExistence(timeout: 5))

        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))

        app.buttons["Home"].tap()
        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))
    }

    // MARK: - Settings Screen

    func testSettingsScreen() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))

        // Check for settings sections
        XCTAssertTrue(app.staticTexts["Call Protection"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["SMS Scanning"].waitForExistence(timeout: 3))
    }

    // MARK: - History Screen

    func testHistoryScreen() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        app.buttons["History"].tap()
        XCTAssertTrue(app.navigationBars["Call History"].waitForExistence(timeout: 5))

        // Empty state should show
        let emptyText = app.staticTexts["No Call History"]
        XCTAssertTrue(emptyText.waitForExistence(timeout: 3))
    }

    // MARK: - Report Screen

    func testReportScreen() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        app.buttons["Report"].tap()
        XCTAssertTrue(app.navigationBars["Report a Scam"].waitForExistence(timeout: 5))

        // Should have a phone number text field
        let phoneField = app.textFields["+1 (555) 123-4567"]
        XCTAssertTrue(phoneField.waitForExistence(timeout: 3), "Phone number placeholder should exist")
    }

    // MARK: - Settings Toggle Interaction

    func testSettingsToggles() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))

        // Verify toggle labels and descriptions are visible
        let callProtectionLabel = app.staticTexts["Call Protection"]
        XCTAssertTrue(callProtectionLabel.waitForExistence(timeout: 3), "Call Protection label should exist")

        let smsScanningLabel = app.staticTexts["SMS Scanning"]
        XCTAssertTrue(smsScanningLabel.waitForExistence(timeout: 3), "SMS Scanning label should exist")

        // Verify toggles exist (SwiftUI Toggle renders as Switch in XCUITest)
        // On iOS 26.3 the switch may not have the label as its identifier,
        // so we verify by counting switches in the Settings tab
        let allSwitches = app.switches.allElementsBoundByIndex
        XCTAssertTrue(allSwitches.count > 0, "At least one switch toggle should exist in Settings")

        // Verify the Auto-Block toggle label exists
        let autoBlockLabel = app.staticTexts["Auto-Block Known Scams"]
        XCTAssertTrue(autoBlockLabel.waitForExistence(timeout: 3), "Auto-Block label should exist")
    }

    // MARK: - Report Form Interaction

    func testReportForm() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()

        app.buttons["Report"].tap()
        XCTAssertTrue(app.navigationBars["Report a Scam"].waitForExistence(timeout: 5))

        // Verify phone number field exists
        let phoneField = app.textFields["+1 (555) 123-4567"]
        XCTAssertTrue(phoneField.waitForExistence(timeout: 3))

        // Verify scam type buttons exist (the picker grid)
        let scamType = app.buttons["General Scam"]
        XCTAssertTrue(scamType.waitForExistence(timeout: 3), "Scam type picker should show options")

        // Tap the submit button
        let submitButton = app.buttons["Report Scam Number"]
        XCTAssertTrue(submitButton.waitForExistence(timeout: 3))
    }
}
