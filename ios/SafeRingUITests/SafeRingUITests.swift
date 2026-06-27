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
}
