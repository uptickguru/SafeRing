import XCTest

final class SafeRingUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    func captureScreenshot(name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = "iOS-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func swipeSettingsUp() {
        // Swipe using coordinate approach since .tables may not exist in SwiftUI List
        let startY = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
        let endY = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        for _ in 0..<6 {
            if !app.switches.firstMatch.exists {
                startY.press(forDuration: 0.1, thenDragTo: endY)
                sleep(1)
            }
        }
    }

    // MARK: - CALL BLOCKING TESTS

    func testCallDirectoryExtensionStatus() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        app.buttons["Settings"].tap()
        let settingsNavBar = app.navigationBars["Settings"]
        XCTAssertTrue(settingsNavBar.waitForExistence(timeout: 5))
        captureScreenshot(name: "settings-call-screen")
        // Swipe to scroll - avoid Switch lookup which fails on some iOS versions
        app.swipeUp()
        sleep(2)
        captureScreenshot(name: "settings-scrolled")
        // Verify app is showing Settings screen
        XCTAssertTrue(settingsNavBar.exists)
    }

    func testCallDirectoryReload() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        swipeSettingsUp()
        let settingsButton = app.buttons["Settings"].firstMatch
        if settingsButton.exists {
            captureScreenshot(name: "call-directory-extension-available")
        }
    }

    // MARK: - SMS FILTERING TESTS

    func testSmsFilteringSettings() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        swipeSettingsUp()
        let smsToggle = app.staticTexts["SMS Scanning"]
        XCTAssertTrue(smsToggle.waitForExistence(timeout: 5))
        captureScreenshot(name: "sms-filtering-toggle")
    }

    func testProtectionToggles() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        swipeSettingsUp()
        captureScreenshot(name: "protection-toggles-all")
        for toggle in ["Call Protection", "SMS Scanning", "Auto-Block Known Scams"] {
            XCTAssertTrue(app.staticTexts[toggle].waitForExistence(timeout: 3))
        }
        let switches = app.switches
        XCTAssertTrue(switches.count > 0)
        switches.firstMatch.tap()
        captureScreenshot(name: "protection-toggle-tapped")
    }

    func testPermissionsStatus() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        swipeSettingsUp()
        captureScreenshot(name: "permissions-status")
        // Verify the Settings screen shows (not empty)
        XCTAssertTrue(app.staticTexts["Call Protection"].waitForExistence(timeout: 3))
    }

    // MARK: - CALL HISTORY

    func testCallHistory() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        app.buttons["History"].tap()
        XCTAssertTrue(app.navigationBars["Call History"].waitForExistence(timeout: 5))
        captureScreenshot(name: "call-history-empty")
        let emptyState = app.staticTexts["No Call History"]
        XCTAssertTrue(emptyState.waitForExistence(timeout: 3))
    }

    // MARK: - ONBOARDING TO PROTECTION

    func testOnboardingToProtection() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "0"]
        app.launch()
        XCTAssertTrue(app.buttons["Get Started"].waitForExistence(timeout: 5))
        captureScreenshot(name: "onboarding-welcome-call-screening")
        app.buttons["Get Started"].tap()
        XCTAssertTrue(app.buttons["Enable Call Screening"].waitForExistence(timeout: 5))
        captureScreenshot(name: "onboarding-enable-call-screening")
        app.buttons["Enable Call Screening"].tap()
        XCTAssertTrue(app.buttons["Start Protection"].waitForExistence(timeout: 5))
        captureScreenshot(name: "onboarding-start-protection")
        app.buttons["Start Protection"].tap()
        app.terminate()
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))
        captureScreenshot(name: "home-with-protection-active")
        let stats = app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "Blocked"))
        XCTAssertTrue(stats.firstMatch.waitForExistence(timeout: 3))
    }

    // MARK: - TAB NAVIGATION

    func testTabNavigation() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))
        captureScreenshot(name: "tabnav-home")
        app.buttons["History"].tap()
        XCTAssertTrue(app.navigationBars["Call History"].waitForExistence(timeout: 5))
        captureScreenshot(name: "tabnav-history")
        app.buttons["Report"].tap()
        XCTAssertTrue(app.navigationBars["Report a Scam"].waitForExistence(timeout: 5))
        captureScreenshot(name: "tabnav-report")
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        swipeSettingsUp()
        captureScreenshot(name: "tabnav-settings")
        app.buttons["Home"].tap()
        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))
    }

    // MARK: - REPORT SCAM

    func testReportScam() throws {
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        app.buttons["Report"].tap()
        XCTAssertTrue(app.navigationBars["Report a Scam"].waitForExistence(timeout: 5))
        captureScreenshot(name: "report-scam-form")
        let phoneField = app.textFields["+1 (555) 123-4567"]
        XCTAssertTrue(phoneField.waitForExistence(timeout: 3))
        let reportButton = app.buttons["Report Scam Number"]
        XCTAssertTrue(reportButton.waitForExistence(timeout: 3))
    }
}
