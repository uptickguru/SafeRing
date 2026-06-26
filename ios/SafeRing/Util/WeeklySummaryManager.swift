import Foundation
import UserNotifications
import SwiftUI

/// Schedules and manages weekly summary notifications.
///
/// After onboarding, this schedules a local notification every Monday at 10 AM
/// showing the user's scam stats from the past week. This improves retention
/// by proving the app is working even when zero scams are detected.
///
enum WeeklySummaryManager {

    /// Unique identifier for the weekly summary notification.
    private static let notificationId = "weekly-summary"

    /// Schedules the weekly summary. Call after onboarding completion.
    /// Replaces any existing weekly summary schedule.
    static func schedule() {
        let center = UNUserNotificationCenter.current()

        // Request notification permission if not already granted
        center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }

        // Remove any existing weekly summary
        center.removePendingNotificationRequests(withIdentifiers: [notificationId])

        let content = UNMutableNotificationContent()
        content.title = "📊 SafeRing Weekly Summary"
        content.body = "Tap to see your scam protection stats for this week."
        content.sound = .default

        // Schedule for Monday at 10:00 AM
        var dateComponents = DateComponents()
        dateComponents.weekday = 2  // Monday
        dateComponents.hour = 10
        dateComponents.minute = 0

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: dateComponents,
            repeats: true
        )

        let request = UNNotificationRequest(
            identifier: notificationId,
            content: content,
            trigger: trigger
        )

        center.add(request)
    }

    /// Updates the weekly summary notification body with current stats.
    /// Call after any scam detection event to keep the notification relevant.
    static func updateStats(blocked: Int, filtered: Int) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            guard let request = requests.first(where: { $0.identifier == notificationId }) else { return }

            let content = request.content.mutableCopy() as! UNMutableNotificationContent
            content.body = "\(blocked) calls blocked · \(filtered) SMS filtered this week."
            content.badge = NSNumber(value: blocked + filtered)

            let newRequest = UNNotificationRequest(
                identifier: notificationId,
                content: content,
                trigger: request.trigger
            )
            center.add(newRequest)
        }
    }

    /// Cancels the weekly summary (e.g., when user disables protection).
    static func cancel() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [notificationId])
    }
}
