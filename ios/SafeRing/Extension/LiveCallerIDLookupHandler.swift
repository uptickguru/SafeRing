import Foundation
import CallKit

/// CallKit Live Caller ID extension for SafeRing (iOS 18+).
///
/// # Architecture
/// This extension provides network-backed caller ID identification for iOS 18+.
/// It uses the privacy-preserving HMAC/token scheme (M1) to query the backend.
///
/// # Data Flow
/// 1. Phone number is HMAC-SHA256 hashed locally (no raw number sent)
/// 2. Token generated from hash + install-specific secret
/// 3. Token sent to backend for identification
/// 4. Backend returns scam label (no number correlation possible)
/// 5. Label surfaced through Apple's CallKit ID UI
///
/// # Security
/// - Phone numbers are hashed with HMAC-SHA256 (not plain SHA-256)
/// - Token generation uses per-install secret key (Keychain)
/// - Backend cannot correlate numbers across users
/// - Falls back to cached list if API unavailable
///
/// # Fallback
/// If the API or entitlement is unavailable, the extension gracefully
/// degrades to using only the locally cached scam list.
///
final class LiveCallerIDLookupHandler: CXCallDirectoryProvider {

    private let lookupService: LiveCallerIDLookupService

    init(lookupService: LiveCallerIDLookupService) {
        self.lookupService = lookupService
    }

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        NSLog("[SafeRing] LiveCallerIDLookup: update requested")
        context.delegate = self

        // TODO: Implement live lookup logic
        // This will query the backend using the HMAC/token scheme
        // and surface results through Apple's CallKit ID UI
        
        context.completeRequest()
    }
}

extension LiveCallerIDLookupHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for context: CXCallDirectoryExtensionContext, withError error: Error) {
        NSLog("[SafeRing] LiveCallerIDLookup context failed: \(error.localizedDescription)")
    }
}
