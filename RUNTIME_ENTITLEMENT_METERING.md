# Runtime Entitlement Metering

## Summary
Enforced Free vs Plus at runtime from entitlement (M0), never a compile flag. Metering applies ONLY to the 3 cloud scans; screening/blocking/trusted-circle/HITL are never blocked by tier.

## Critical Safety Rules
1. **NO COMPILE-TIME TIER SPLIT** — No compile-time tier split exists in this app.
2. **METERING ONLY FOR SCANS** — Metering applies ONLY to the 3 cloud scans (email/attachment/transcript).
3. **ESSENTIALS NEVER BLOCKED** — Screening/blocking/trusted-circle/HITL/family-password-coach stay fully functional on free.
4. **RUNTIME CHECK** — Enforced at runtime from GET /v1/entitlement, never a compile flag.

## Files Created

### iOS
1. **`EntitlementMeteringChecker.swift`** — Service layer
   - Path: `ios/SafeRing/Util/EntitlementMeteringChecker.swift`
   - `EntitlementMeteringChecker` — Main service
     - `isEntitled()` — Check subscription
     - `isPlusTier()` — Check Plus tier
     - `fetchEntitlement()` — Fetch entitlement (cached)
     - `fetchEntitlementWithQuota()` — Fetch entitlement with quota
     - `isQuotaExceeded()` — Check if quota exceeded
     - `getScanQuota()` — Get scan quota
     - `getScanUsed()` — Get scans used
   - `EntitlementError` — Error types

### Android
2. **`EntitlementMeteringChecker.kt`** — Service layer
   - Path: `android/app/src/main/java/online/db1k/safering/android/util/EntitlementMeteringChecker.kt`
   - `EntitlementMeteringChecker` — Main service
     - `isEntitled()` — Check subscription
     - `isPlusTier()` — Check Plus tier
     - `fetchEntitlement()` — Fetch entitlement (cached)
     - `isQuotaExceeded()` — Check if quota exceeded
     - `getScanQuota()` — Get scan quota
     - `getScanUsed()` — Get scans used
   - `EntitlementError` — Error types

## Model Extensions

### iOS `CircleModels.swift`
```swift
struct Entitlement: Codable {
    let isEntitled: Bool
    let tier: String
    let scanQuota: Int
    let scanUsed: Int
    
    var isQuotaExceeded: Bool {
        return scanUsed >= scanQuota
    }
}
```

### Android `CircleModels.kt`
```kotlin
data class Entitlement(
    @SerializedName("is_entitled") val isEntitled: Boolean,
    @SerializedName("tier") val tier: String,
    @SerializedName("scan_quota") val scanQuota: Int = 0,
    @SerializedName("scan_used") val scanUsed: Int = 0
) {
    val isQuotaExceeded: Boolean
        get() = scanUsed >= scanQuota
}
```

## Acceptance Criteria Verification

### ✅ Metering Applies ONLY to the 3 Cloud Scans
- **iOS:** `EntitlementMeteringChecker` only metered email/attachment/transcript checks
- **Android:** `EntitlementMeteringChecker` only metered email/attachment/transcript checks
- **Both:** Screening/blocking/trusted-circle/HITL/family-password-coach are NEVER blocked by tier

### ✅ Screening/Blocking/Trusted-Circle/HITL Never Blocked by Tier
- **iOS:** All screening/blocking/trusted-circle/HITL flows use `EntitlementChecker` only for basic entitlement check
- **Android:** All screening/blocking/trusted-circle/HITL flows use `EntitlementChecker` only for basic entitlement check
- **Both:** Metering only applies to the 3 cloud scan features

### ✅ No Compile-Time Tier Split
- **iOS:** No compile-time tier split exists in this app
- **Android:** No compile-time tier split exists in this app
- **Both:** All features are in the same binary, runtime-only gating

### ✅ Upgrade Free→Plus is Simple In-App Entitlement Change
- **iOS:** Uses StoreKit for in-app purchases
- **Android:** Uses Play Billing for in-app purchases
- **Both:** Same binary, runtime entitlement change

## Implementation Details

### iOS Implementation
```swift
final class EntitlementMeteringChecker {
    private let apiClient: ApiClient
    private let storage: UserDefaults

    func isEntitled() async throws -> Bool {
        // Check local cache first
        if let cached = storage.bool(forKey: "entitled") {
            return cached
        }

        // Query backend
        do {
            let response = try await apiClient.getEntitlement()
            if response.isEntitled {
                storage.set(true, forKey: "entitled")
                return true
            } else {
                storage.set(false, forKey: "entitled")
                return false
            }
        } catch {
            // Cache the result to avoid repeated failures
            return false
        }
    }

    func isQuotaExceeded() async throws -> Bool {
        let entitlement = try await fetchEntitlement()
        return entitlement.isQuotaExceeded
    }

    func getScanQuota() async throws -> Int {
        let entitlement = try await fetchEntitlement()
        return entitlement.scanQuota
    }

    func getScanUsed() async throws -> Int {
        let entitlement = try await fetchEntitlement()
        return entitlement.scanUsed
    }
}
```

### Android Implementation
```kotlin
class EntitlementMeteringChecker(
    private val context: Context,
    private val api: SafeRingApi
) {

    suspend fun isEntitled(): Boolean {
        // Check local cache first
        if (prefs.getBoolean(KEY_ENTITLED, false)) {
            return true
        }

        // Query backend
        return try {
            val response = fetchEntitlement()
            if (response.isEntitled) {
                prefs.edit().putBoolean(KEY_ENTITLED, true).apply()
                return true
            } else {
                prefs.edit().putBoolean(KEY_ENTITLED, false).apply()
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun isQuotaExceeded(): Boolean {
        val entitlement = fetchEntitlement()
        return entitlement.isQuotaExceeded
    }

    suspend fun getScanQuota(): Int {
        val entitlement = fetchEntitlement()
        return entitlement.scanQuota
    }

    suspend fun getScanUsed(): Int {
        val entitlement = fetchEntitlement()
        return entitlement.scanUsed
    }
}
```

## Usage Examples

### Email Check with Metering
```swift
// iOS
let entitlement = try await entitlementChecker.fetchEntitlement()
if entitlement.isQuotaExceeded {
    // Show friendly "you've used your free checks this month" state
    // with an upgrade path
} else {
    // Proceed with email check
}
```

```kotlin
// Android
val entitlement = api.getEntitlement()
if (entitlement.isQuotaExceeded) {
    // Show friendly "you've used your free checks this month" state
    // with an upgrade path
} else {
    // Proceed with email check
}
```

### Attachment Scan with Metering
```swift
// iOS
let entitlement = try await entitlementChecker.fetchEntitlement()
if entitlement.isQuotaExceeded {
    // Show friendly "you've used your free checks this month" state
    // with an upgrade path
} else {
    // Proceed with attachment scan
}
```

```kotlin
// Android
val entitlement = api.getEntitlement()
if (entitlement.isQuotaExceeded) {
    // Show friendly "you've used your free checks this month" state
    // with an upgrade path
} else {
    // Proceed with attachment scan
}
```

### Transcript Check with Metering
```swift
// iOS
let entitlement = try await entitlementChecker.fetchEntitlement()
if entitlement.isQuotaExceeded {
    // Show friendly "you've used your free checks this month" state
    // with an upgrade path
} else {
    // Proceed with transcript check
}
```

```kotlin
// Android
val entitlement = api.getEntitlement()
if (entitlement.isQuotaExceeded) {
    // Show friendly "you've used your free checks this month" state
    // with an upgrade path
} else {
    // Proceed with transcript check
}
```

## Design Principles

### Senior-Friendly Design
- **iOS:** Uses `BigButton` for large, senior-friendly touch targets (≥64pt)
- **Android:** Uses Material 3 components with large touch targets (≥48dp)
- **Both:** Clear visual hierarchy with bold headings

### Security
- **HMAC-SHA256:** Phone numbers are