import Foundation
import CryptoKit
import SwiftUI

/// Opt-in exceptional capture: encrypt sender+message for ops OSINT, seed filter hash.
enum ExceptionalCaptureService {
    static let consentVersion = "exceptional-v1"

    /// Server public key placeholder (32-byte Curve25519). Replace in production builds.
    /// Generate: `openssl` / CryptoKit; keep private key only on ops host.
    private static let serverPubKeyB64 = "" // empty → HTTPS-only debug envelope

    struct PlainPayload: Codable {
        var sender_e164: String
        var message_body: String
        var note: String
        var device_ts: String
    }

    static func senderHash(_ e164: String) -> String {
        let digits = e164.filter(\.isNumber)
        let data = Data(digits.utf8)
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func submit(
        senderE164: String,
        messageBody: String,
        note: String,
        householdLabel: String,
        baseURL: URL = URL(string: "https://safering.deathbyathousand.com")!
    ) async throws -> String {
        guard FilterRulesStore.shared.exceptionalCaptureEnabled else {
            throw CaptureError.notEnabled
        }
        let hash = senderHash(senderE164)
        let plain = PlainPayload(
            sender_e164: senderE164,
            message_body: messageBody,
            note: note,
            device_ts: ISO8601DateFormatter().string(from: Date())
        )
        let plainData = try JSONEncoder().encode(plain)

        var alg = "https-only-v0"
        var ciphertextB64 = ""
        var nonceB64 = ""
        var ephPub = ""
        var debugPlain: [String: String]? = [
            "sender_e164": senderE164,
            "message_body": messageBody,
            "note": note
        ]

        if let pub = loadServerPubKey() {
            // Sealed box to server static key
            let box = try sealedBox(plain: plainData, serverPub: pub)
            ciphertextB64 = box.ciphertext.base64EncodedString()
            nonceB64 = box.nonce.base64EncodedString()
            ephPub = box.ephemeralPub.base64EncodedString()
            alg = "x25519-chacha-v1"
            debugPlain = nil
        }

        var body: [String: Any] = [
            "consent_version": consentVersion,
            "household_label": householdLabel,
            "channel": "sms",
            "sender_hash": hash,
            "alg": alg,
            "ciphertext_b64": ciphertextB64,
            "nonce_b64": nonceB64,
            "ephemeral_pub_b64": ephPub,
            "created_at": ISO8601DateFormatter().string(from: Date())
        ]
        if let debugPlain {
            body["debug_plain"] = debugPlain
        }

        var req = URLRequest(url: baseURL.appendingPathComponent("v1/exceptional/capture"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("1", forHTTPHeaderField: "X-SafeRing-Exceptional")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw CaptureError.server
        }
        // Seed local block immediately so Message Filter / future checks junk
        FilterRulesStore.shared.addBlockedSender(senderE164)

        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let id = obj["case_id"] as? String {
            return id
        }
        return hash
    }

    private struct BoxParts {
        var ciphertext: Data
        var nonce: Data
        var ephemeralPub: Data
    }

    private static func loadServerPubKey() -> Curve25519.KeyAgreement.PublicKey? {
        guard !serverPubKeyB64.isEmpty,
              let d = Data(base64Encoded: serverPubKeyB64),
              let k = try? Curve25519.KeyAgreement.PublicKey(rawRepresentation: d) else {
            return nil
        }
        return k
    }

    private static func sealedBox(plain: Data, serverPub: Curve25519.KeyAgreement.PublicKey) throws -> BoxParts {
        let eph = Curve25519.KeyAgreement.PrivateKey()
        let shared = try eph.sharedSecretFromKeyAgreement(with: serverPub)
        let sym = shared.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: Data("safering-exceptional-v1".utf8),
            sharedInfo: Data("capture".utf8),
            outputByteCount: 32
        )
        let sealed = try ChaChaPoly.seal(plain, using: sym)
        return BoxParts(
            ciphertext: sealed.ciphertext + sealed.tag,
            nonce: Data(sealed.nonce),
            ephemeralPub: eph.publicKey.rawRepresentation
        )
    }

    enum CaptureError: LocalizedError {
        case notEnabled, server
        var errorDescription: String? {
            switch self {
            case .notEnabled: return "Turn on Exceptional investigation in Settings first."
            case .server: return "Could not reach SafeRing investigation service."
            }
        }
    }
}
