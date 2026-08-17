import Foundation
import UserNotifications

enum WeeklySummaryManager {
    private static let notificationId = "weekly-summary"

    /// Never prompts. Only schedules if already authorized.
    static func schedule() {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
                || settings.authorizationStatus == .ephemeral else { return }
            Self.enqueueWeekly()
        }
    }

    /// Explicit opt-in from Settings only.
    static func requestPermissionIfNeeded() {
        // Intentionally empty in v1 — notification prompt was covering Home/Onboarding.
        // Wire from Settings later if product wants weekly check-ins.
    }

    private static func enqueueWeekly() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [notificationId])
        let content = UNMutableNotificationContent()
        content.title = "SafeRing weekly check-in"
        content.body = "Tap to review protection for this week."
        content.sound = .default
        var dc = DateComponents()
        dc.weekday = 2
        dc.hour = 10
        let trigger = UNCalendarNotificationTrigger(dateMatching: dc, repeats: true)
        center.add(UNNotificationRequest(identifier: notificationId, content: content, trigger: trigger))
    }

    static func updateStats(blocked: Int, filtered: Int) {}
    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [notificationId])
    }
}
