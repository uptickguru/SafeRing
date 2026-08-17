import SwiftUI

/// Full-viewport onboarding on any device size.
struct OnboardingView: View {

    let onComplete: () -> Void

    @ObservedObject private var household = HouseholdStore.shared

    @State private var step = 0
    @State private var password = ""
    @State private var passwordConfirm = ""
    @State private var showContacts = false
    @State private var errorMessage: String?

    private let total = 4

    var body: some View {
        GeometryReader { geo in
            let h = max(geo.size.height, 1)
            let side = max(18, min(geo.size.width * 0.06, 28))

            VStack(spacing: 0) {
                HStack(spacing: 6) {
                    ForEach(0..<total, id: \.self) { i in
                        Capsule()
                            .fill(i <= step ? SR.gold : SR.line)
                            .frame(width: i == step ? 22 : 7, height: 6)
                    }
                }
                .padding(.top, max(10, h * 0.02))
                .padding(.bottom, 8)

                Group {
                    switch step {
                    case 0: welcome(h: h)
                    case 1: nameStep
                    case 2: personStep
                    default: passwordStep
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                if let errorMessage {
                    Text(errorMessage)
                        .font(SR.font(16, .medium))
                        .foregroundStyle(SR.help)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, side)
                        .padding(.bottom, 8)
                }

                Button(action: advance) {
                    Text(step == total - 1 ? "Begin" : "Continue")
                        .font(SR.font(18, .semibold))
                        .tracking(1.0)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: max(54, min(h * 0.08, 62)))
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(SR.ink)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(SR.gold.opacity(0.4), lineWidth: 0.8)
                        )
                }
                .buttonStyle(PressSoft())
                .padding(.horizontal, side)

                if step > 0 {
                    Button("Back") {
                        errorMessage = nil
                        withAnimation(.easeInOut(duration: 0.18)) { step -= 1 }
                    }
                    .font(SR.font(16, .medium))
                    .foregroundStyle(SR.mute)
                    .frame(height: 44)
                } else {
                    Color.clear.frame(height: 44)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .padding(.bottom, geo.safeAreaInsets.bottom > 0 ? 0 : 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(SR.canvas.ignoresSafeArea())
        .sheet(isPresented: $showContacts) {
            ContactPicker(
                onPick: { name, number in
                    if !name.isEmpty { household.trustedContactName = name }
                    household.trustedContactNumber = number
                    showContacts = false
                },
                onCancel: { showContacts = false }
            )
            .ignoresSafeArea()
        }
    }

    private func welcome(h: CGFloat) -> some View {
        VStack(spacing: 18) {
            Spacer(minLength: h * 0.06)
            ZStack {
                Circle()
                    .stroke(SR.gold.opacity(0.35), lineWidth: 1)
                    .frame(width: min(120, h * 0.16), height: min(120, h * 0.16))
                Image(systemName: "shield")
                    .font(.system(size: min(44, h * 0.055), weight: .ultraLight))
                    .foregroundStyle(SR.ink)
            }
            Text("SAFERING")
                .font(SR.font(12, .semibold))
                .tracking(4)
                .foregroundStyle(SR.gold)
            Text("A quiet promise.")
                .font(SR.font(min(32, h * 0.045), .regular))
                .foregroundStyle(SR.ink)
            Text("Texts someone you trust\nwhen something feels wrong.")
                .font(SR.font(18, .regular))
                .foregroundStyle(SR.mute)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 28)
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var nameStep: some View {
        formShell(title: "Your name", hint: "Your person sees this in the alert.") {
            field("Example: Helen", text: $household.ownerDisplayName)
                .textContentType(.name)
        }
    }

    private var personStep: some View {
        formShell(title: "Who we reach", hint: "Usually a child or spouse.") {
            VStack(spacing: 12) {
                field("Their name", text: $household.trustedContactName)
                    .textContentType(.name)
                field("Their phone number", text: $household.trustedContactNumber)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                Button { showContacts = true } label: {
                    Text("Choose from Contacts")
                        .font(SR.font(16, .medium))
                        .foregroundStyle(SR.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(SR.surface)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(SR.line, lineWidth: 0.8)
                        )
                }
            }
        }
    }

    private var passwordStep: some View {
        formShell(title: "Family password", hint: "Ask them this if someone claims to be family. Not a bank PIN.") {
            VStack(spacing: 12) {
                secure("Password", text: $password)
                secure("Type it again", text: $passwordConfirm)
            }
        }
    }

    private func formShell<C: View>(title: String, hint: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Spacer().frame(height: 12)
            Text(title)
                .font(SR.font(28, .regular))
                .foregroundStyle(SR.ink)
            Text(hint)
                .font(SR.font(16, .regular))
                .foregroundStyle(SR.mute)
                .fixedSize(horizontal: false, vertical: true)
            content()
            Spacer()
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func field(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .font(SR.font(20, .regular))
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(SR.surface))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(SR.line, lineWidth: 0.8))
    }

    private func secure(_ placeholder: String, text: Binding<String>) -> some View {
        SecureField(placeholder, text: text)
            .font(SR.font(20, .regular))
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(SR.surface))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(SR.line, lineWidth: 0.8))
            .textContentType(.newPassword)
    }

    private func advance() {
        errorMessage = nil
        switch step {
        case 0:
            withAnimation { step = 1 }
        case 1:
            if household.ownerDisplayName.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 {
                errorMessage = "Type your name."
                return
            }
            withAnimation { step = 2 }
        case 2:
            if HouseholdStore.normalizeToE164(household.trustedContactNumber).filter(\.isNumber).count < 10 {
                errorMessage = "Enter a real phone number."
                return
            }
            if household.trustedContactName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                household.trustedContactName = "My person"
            }
            withAnimation { step = 3 }
        case 3:
            let trimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.count < 3 {
                errorMessage = "Password needs at least 3 characters."
                return
            }
            if trimmed != passwordConfirm.trimmingCharacters(in: .whitespacesAndNewlines) {
                errorMessage = "Passwords do not match."
                return
            }
            household.setFamilyPassword(trimmed)
            password = ""
            passwordConfirm = ""
            guard household.isConfigured else {
                errorMessage = "Missing name or phone. Go back and check."
                return
            }
            onComplete()
        default:
            break
        }
    }
}
