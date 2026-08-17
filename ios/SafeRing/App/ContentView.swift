import SwiftUI
import MessageUI

struct ContentView: View {

    @State private var selectedTab: Tab = .home
    @StateObject private var homeViewModel = HomeViewModel()
    @StateObject private var signaler = HelpSignaler.shared
    @StateObject private var callObserver = CallEndObserver.shared
    @ObservedObject private var household = HouseholdStore.shared

    @State private var showCheckIn = false

    enum Tab: String {
        case home, history, settings
    }

    init() {
        // Solid tab bar — never floating glass over Call / tools
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        let barBG = UIColor(red: 0.97, green: 0.965, blue: 0.955, alpha: 1)
        appearance.backgroundColor = barBG
        appearance.shadowColor = UIColor(white: 0, alpha: 0.08)
        let item = UITabBarItemAppearance()
        let font = UIFont.systemFont(ofSize: 11, weight: .medium)
        let muted = UIColor(red: 0.55, green: 0.53, blue: 0.50, alpha: 1)
        let selected = UIColor(red: 0.18, green: 0.17, blue: 0.16, alpha: 1)
        item.normal.iconColor = muted
        item.normal.titleTextAttributes = [.font: font, .foregroundColor: muted]
        item.selected.iconColor = selected
        item.selected.titleTextAttributes = [.font: font, .foregroundColor: selected]
        appearance.stackedLayoutAppearance = item
        appearance.inlineLayoutAppearance = item
        appearance.compactInlineLayoutAppearance = item
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
        UITabBar.appearance().isTranslucent = false
        UITabBar.appearance().tintColor = selected
        UITabBar.appearance().unselectedItemTintColor = muted
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(viewModel: homeViewModel)
                .tabItem { Label("Home", systemImage: "house") }
                .tag(Tab.home)
                .toolbarBackground(Color(red: 0.97, green: 0.965, blue: 0.955), for: .tabBar)
                .toolbarBackground(.visible, for: .tabBar)

            NavigationStack { CallHistoryView() }
                .tabItem { Label("History", systemImage: "clock") }
                .tag(Tab.history)

            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(SR.canvas)
        .tint(SR.gold)

        .sheet(item: Binding(
            get: { signaler.draft },
            set: { if $0 == nil { signaler.cancelDraft() } }
        )) { draft in
            MessageComposeView(
                recipients: draft.recipients,
                body: draft.body,
                onFinish: { result in
                    if result == .sent { signaler.markSent() }
                    else { signaler.cancelDraft() }
                }
            )
            .ignoresSafeArea()
        }
        .sheet(isPresented: $showCheckIn) {
            AfterCallCheckInView(
                checkIn: callObserver.pendingCheckIn,
                trustedName: household.trustedContactName,
                onOkay: { callObserver.dismiss(); showCheckIn = false },
                onHelp: {
                    callObserver.dismiss(); showCheckIn = false
                    homeViewModel.requestHelp(.afterCall)
                },
                onCall: {
                    callObserver.dismiss(); showCheckIn = false
                    homeViewModel.callPerson()
                }
            )
            .presentationDetents([.medium, .large])
        }
        .onChange(of: callObserver.pendingCheckIn) { _, newValue in
            showCheckIn = newValue != nil
        }
    }
}

private struct AfterCallCheckInView: View {
    let checkIn: CallCheckIn?
    let trustedName: String
    let onOkay: () -> Void
    let onHelp: () -> Void
    let onCall: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer().frame(height: 8)
            Text("A CALL ENDED")
                .font(SR.font(12, .semibold))
                .tracking(2.5)
                .foregroundStyle(SR.gold)
            Text("Was everything alright?")
                .font(SR.font(26, .regular))
                .foregroundStyle(SR.ink)
            Text("If anyone asked for money, passwords, or secrecy, reach \(trustedName.isEmpty ? "your person" : trustedName).")
                .font(SR.font(17, .regular))
                .foregroundStyle(SR.mute)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 20)
            ElegantPrimary(title: "It felt wrong — get help", top: SR.help, height: 64, titleSize: 18, action: onHelp)
                .padding(.horizontal, 22)
            ElegantPrimary(title: "Call my person", icon: "phone.fill", top: SR.go, height: 56, titleSize: 17, action: onCall)
                .padding(.horizontal, 22)
            Button("It was fine", action: onOkay)
                .font(SR.font(16, .medium))
                .foregroundStyle(SR.mute)
                .padding(.top, 6)
            Spacer()
        }
        .padding(20)
        .background(SR.canvas)
    }
}
