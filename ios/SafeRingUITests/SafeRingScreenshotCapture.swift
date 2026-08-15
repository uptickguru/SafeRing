import XCTest

final class SafeRingScreenshotCapture: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    func testCaptureScreenshots() throws {
        // iOS-01: Home screen with stats
        app.launchArguments = ["-hasCompletedOnboarding", "1"]
        app.launch()
        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))
        sleep(2)
        let screenshot1 = XCUIScreen.main.screenshot()
        let attachment1 = XCTAttachment(screenshot: screenshot1)
        attachment1.name = "iOS-01-home-stats"
        attachment1.lifetime = .keepAlways
        add(attachment1)

        // iOS-03: History tab
        app.buttons["History"].tap()
        XCTAssertTrue(app.navigationBars["Call History"].waitForExistence(timeout: 5))
        sleep(1)
        let screenshot3 = XCUIScreen.main.screenshot()
        let attachment3 = XCTAttachment(screenshot: screenshot3)
        attachment3.name = "iOS-03-history-tab"
        attachment3.lifetime = .keepAlways
        add(attachment3)

        // iOS-04: Report tab
        app.buttons["Report"].tap()
        XCTAssertTrue(app.navigationBars["Report a Scam"].waitForExistence(timeout: 5))
        sleep(1)
        let screenshot4 = XCUIScreen.main.screenshot()
        let attachment4 = XCTAttachment(screenshot: screenshot4)
        attachment4.name = "iOS-04-report-tab"
        attachment4.lifetime = .keepAlways
        add(attachment4)

        // iOS-02: Settings tab
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        sleep(1)
        let screenshot2 = XCUIScreen.main.screenshot()
        let attachment2 = XCTAttachment(screenshot: screenshot2)
        attachment2.name = "iOS-02-settings-tab"
        attachment2.lifetime = .keepAlways
        add(attachment2)

        // iOS-99: After tests (back to home)
        app.buttons["Home"].tap()
        XCTAssertTrue(app.navigationBars["SafeRing"].waitForExistence(timeout: 5))
        sleep(1)
        let screenshot5 = XCUIScreen.main.screenshot()
        let attachment5 = XCTAttachment(screenshot: screenshot5)
        attachment5.name = "iOS-99-after-tests"
        attachment5.lifetime = .keepAlways
        add(attachment5)
    }
}
