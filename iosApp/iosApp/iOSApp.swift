import SwiftUI
import UIKit
import UserNotifications
import Shared

final class RoyAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        IosPush.shared.refreshPermission()
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
        IosPush.shared.receiveToken(token: token.map { String(format: "%02x", $0) }.joined())
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let data = response.notification.request.content.userInfo
        IosPush.shared.receiveNotification(
            version: (data["v"] as? NSNumber)?.stringValue ?? data["v"] as? String,
            kind: data["kind"] as? String,
            notificationId: data["notificationId"] as? String,
            pointId: data["pointId"] as? String,
            chatId: data["chatId"] as? String,
            proposalId: data["proposalId"] as? String
        )
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(RoyAppDelegate.self) private var appDelegate
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
