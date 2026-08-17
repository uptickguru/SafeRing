import IdentityLookup
import os.log

/// Offline-first SMS/iMessage filter. Optional network deferral for exceptional clients.
final class MessageFilterHandler: ILMessageFilterExtension {}

extension MessageFilterHandler: ILMessageFilterQueryHandling {
    func handle(
        _ queryRequest: ILMessageFilterQueryRequest,
        context: ILMessageFilterExtensionContext,
        completion: @escaping (ILMessageFilterQueryResponse) -> Void
    ) {
        let sender = queryRequest.sender ?? ""
        let body = queryRequest.messageBody ?? ""
        let rules = FilterRulesStore.shared

        // 1) Explicit allow (trusted / family)
        if rules.isAllowedSender(sender) {
            finish(.allow, sender: sender, reason: "allowlist", completion: completion)
            return
        }

        // 2) Known-bad sender
        if rules.isBlockedSender(sender) {
            finish(.junk, sender: sender, reason: "blocklist", completion: completion)
            return
        }

        // 3) Word / phrase list
        if let hit = rules.firstMatchingKeyword(in: body) ?? rules.firstMatchingKeyword(in: sender) {
            finish(.junk, sender: sender, reason: "keyword:\(hit)", completion: completion)
            return
        }

        // 4) URL + urgency
        if rules.looksLikePhishingLinkCombo(body: body) {
            finish(.junk, sender: sender, reason: "link+urgency", completion: completion)
            return
        }

        // 5) Exceptional clients — network assist
        guard rules.networkAssistEnabled else {
            finish(.none, sender: sender, reason: "no_match", completion: completion)
            return
        }

        context.deferQueryRequestToNetwork { networkResponse, error in
            if let error {
                os_log("SafeRing MF network error: %{public}@", log: .default, type: .error, error.localizedDescription)
                self.finish(.none, sender: sender, reason: "network_error", completion: completion)
                return
            }
            let action = Self.parseNetworkAction(networkResponse)
            self.finish(action, sender: sender, reason: "network", completion: completion)
        }
    }

    private func finish(
        _ action: ILMessageFilterAction,
        sender: String,
        reason: String,
        completion: @escaping (ILMessageFilterQueryResponse) -> Void
    ) {
        let response = ILMessageFilterQueryResponse()
        response.action = action
        FilterRulesStore.shared.recordDecision(
            sender: sender,
            action: Self.label(action),
            reason: reason
        )
        completion(response)
    }

    private static func label(_ action: ILMessageFilterAction) -> String {
        switch action {
        case .allow: return "allow"
        case .junk: return "junk"
        case .promotion: return "promotion"
        case .transaction: return "transaction"
        case .none: return "none"
        @unknown default: return "unknown"
        }
    }

    /// Server JSON: {"action":"junk"|"allow"|"none"|"promotion"|"transaction"}
    private static func parseNetworkAction(_ response: ILNetworkResponse?) -> ILMessageFilterAction {
        guard let response,
              let obj = try? JSONSerialization.jsonObject(with: response.data) as? [String: Any],
              let raw = (obj["action"] as? String)?.lowercased()
        else { return .none }
        switch raw {
        case "junk", "block", "spam": return .junk
        case "allow", "ham": return .allow
        case "promotion": return .promotion
        case "transaction": return .transaction
        default: return .none
        }
    }
}
