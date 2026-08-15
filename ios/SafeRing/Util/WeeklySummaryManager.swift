import Foundation
import UserNotifications
import SwiftUI

class WeeklySummaryManager {
    
    private static let notificationId = "weekly-summary"
    
    static func schedule() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        center.removePendingNotificationRequests(withIdentifiers: [notificationId])
        
        let content = UNMutableNotificationContent()
        content.title = "📊 SafeRing Weekly Summary"
        content.body = "Tap to see your scam protection stats for this week."
        content.sound = .default
        
        var dateComponents = DateComponents()
        dateComponents.weekday = 2
        dateComponents.hour = 10
        dateComponents.minute = 0
        
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
        let request = UNNotificationRequest(identifier: notificationId, content: content, trigger: trigger)
        center.add(request)
    }
    
    static func updateStats(blocked: Int, filtered: Int) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            guard let request = requests.first(where: { $0.identifier == notificationId }) else { return }
            let content = request.content.mutableCopy() as! UNMutableNotificationContent
            content.body = "\(blocked) calls blocked · \(filtered) SMS filtered this week."
            content.badge = NSNumber(value: blocked + filtered)
            let newRequest = UNNotificationRequest(identifier: notificationId, content: content, trigger: request.trigger)
            center.add(newRequest)
        }
    }
    
    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [notificationId])
    }
}
