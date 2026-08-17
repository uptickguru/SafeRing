import SwiftUI

/// Soft full-screen home — content clears the tab bar; colors quiet and timeless.
struct HomeView: View {

    @StateObject var viewModel: HomeViewModel
    @ObservedObject private var household = HouseholdStore.shared

    @State private var showCheck = false
    @State private var showPassword = false

    private var person: String {
        let n = household.trustedContactName.trimmingCharacters(in: .whitespacesAndNewlines)
        return n.isEmpty ? "my person" : n
    }

    private var ready: Bool { household.isConfigured }

    var body: some View {
        VStack(spacing: 0) {
            header
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 12)

            // Quiet rule
            Rectangle()
                .fill(SR.line)
                .frame(height: 1)
                .padding(.horizontal, 20)
                .padding(.bottom, 14)

            // HELP fills remaining space above the lower stack
            helpButton
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 20)

            VStack(spacing: 10) {
                secondaryButton(
                    title: "Not sure — still text them",
                    fill: SR.caution,
                    action: { viewModel.requestHelp(.help) }
                )
                secondaryButton(
                    title: "Call \(person)",
                    fill: SR.go,
                    icon: "phone.fill",
                    action: { viewModel.callPerson() }
                )

                HStack(spacing: 10) {
                    toolTile("Message", "text.magnifyingglass") { showCheck = true }
                    toolTile("Code", "lock.fill") { showPassword = true }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .padding(.bottom, 12)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(SR.canvas.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showCheck) {
            PasteCheckView(
                household: household,
                onNeedHelp: { reason in
                    showCheck = false
                    viewModel.requestHelp(reason)
                },
                onCallPerson: {
                    showCheck = false
                    viewModel.callPerson()
                }
            )
        }
        .alert("Family password", isPresented: $showPassword) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(passwordText)
        }
        .alert("Could not reach your person", isPresented: $viewModel.showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.lastError ?? "")
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text("SAFERING")
                    .font(SR.font(11, .semibold))
                    .tracking(3.0)
                    .foregroundStyle(SR.gold)
                Text("Protection")
                    .font(SR.font(24, .regular))
                    .foregroundStyle(SR.ink)
            }
            Spacer(minLength: 8)
            StatusPill(text: ready ? "Ready · \(person)" : "Needs setup", ok: ready)
        }
    }

    private var helpButton: some View {
        Button(action: { viewModel.requestHelp(.money) }) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(SR.help)
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(SR.gold.opacity(0.22), lineWidth: 0.8)
                VStack(spacing: 12) {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 30, weight: .medium))
                    Text("HELP")
                        .font(SR.font(36, .semibold))
                        .tracking(3)
                    Text("Text \(person)")
                        .font(SR.font(17, .regular))
                        .opacity(0.9)
                }
                .foregroundStyle(Color.white.opacity(0.96))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .shadow(color: .black.opacity(0.08), radius: 10, y: 4)
        }
        .buttonStyle(PressSoft())
        .accessibilityLabel("Help. Texts \(person).")
    }

    private func secondaryButton(title: String, fill: Color, icon: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .medium))
                }
                Text(title)
                    .font(SR.font(16, .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .foregroundStyle(.white.opacity(0.96))
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(fill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.white.opacity(0.08), lineWidth: 0.6)
            )
        }
        .buttonStyle(PressSoft())
    }

    private func toolTile(_ title: String, _ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(SR.gold)
                    .frame(width: 22)
                Text(title)
                    .font(SR.font(15, .medium))
                    .foregroundStyle(SR.ink)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(SR.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(SR.line, lineWidth: 1)
            )
        }
        .buttonStyle(PressSoft())
    }

    private var passwordText: String {
        if let password = household.familyPassword() {
            return "Ask them first. Yours is: \(password)\n\nHang up if they do not know it."
        }
        return "No password yet. Add one in Settings."
    }
}
