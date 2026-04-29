# 📦 Data Models

Every data class in the project, explained field by field.

---

## Domain Models

### `SmsMessage`

Raw SMS data as read from the Android `ContentResolver`.

```kotlin
data class SmsMessage(
    val sender: String,      // SMS address field (e.g. "VD-HDFCBK", "+919876543210")
    val body: String,        // Full message text
    val timestamp: Long,     // Epoch millis from Telephony.Sms.DATE column
    val dateString: String   // Pre-formatted display string: "dd/MM/yyyy HH:mm"
)
```

`SmsMessage` is created only in `SmsReader` (manual scan) or `SmsReceiver` (live detection). It is never mutated after creation.

---

### `ParsedTransaction`

The central model of the app. Starts from a parsed SMS and accumulates user-selected Firefly metadata before being submitted.

```kotlin
data class ParsedTransaction(
    // ── Parsed from SMS (immutable after creation) ─────────────────────────
    val amount: Double,              // Raw parsed amount
    val type: TransactionType,       // Raw parsed type (DEBIT / CREDIT / UNKNOWN)
    val rawMessage: String,          // Original SMS body — shown in Debug and as notes
    val sender: String,              // SMS sender address
    val timestamp: Long,             // Original SMS timestamp (used as transaction date)

    // ── User corrections ────────────────────────────────────────────────────
    var correctedAmount: Double?,    // Set when user edits the amount field
    var correctedType: TransactionType?,  // Set when user toggles DEBIT/CREDIT chip

    // ── Firefly metadata (user-selected in TransactionScreen) ────────────────
    var description: String,         // Transaction description (defaults to "SMS: ...")
    var categoryName: String?,       // Firefly category name (null = not set)
    var selectedTags: MutableList<String>,  // Firefly tag names
    var budgetId: String?,           // Firefly budget ID (null = not set)
    var budgetName: String?,         // Display name of selected budget
    var sourceAccountId: String?,    // Asset account ID (withdrawal source / deposit destination)
    var sourceAccountName: String?,  // Display name
    var destinationAccountId: String?,   // Expense account ID (withdrawal dest)
    var destinationAccountName: String?, // Display name or free-text

    // ── Tracking ─────────────────────────────────────────────────────────────
    var status: SendStatus           // PENDING → SENDING → SENT | FAILED
)
```

**Computed properties:**

```kotlin
val effectiveAmount: Double    // correctedAmount ?: amount
val effectiveType: TransactionType  // correctedType ?: type
```

These are what get sent to Firefly III — original values unless the user has corrected them.

---

### `TransactionType`

```kotlin
enum class TransactionType {
    DEBIT,    // Money leaving the account (withdrawal in Firefly)
    CREDIT,   // Money entering the account (deposit in Firefly)
    UNKNOWN;  // Parser couldn't determine type (defaults to withdrawal)

    fun toFireflyType(): String = when (this) {
        DEBIT   -> "withdrawal"
        CREDIT  -> "deposit"
        UNKNOWN -> "withdrawal"
    }
}
```

---

### `SendStatus`

```kotlin
enum class SendStatus {
    PENDING,   // Transaction not yet submitted
    SENDING,   // Network call in progress
    SENT,      // Successfully created in Firefly III
    FAILED     // Network or API error
}
```

`SendStatus` drives the appearance of the "Send to Firefly" button:

| Status | Button text | Button colour | Enabled? |
|---|---|---|---|
| `PENDING` | "Send to Firefly" | Primary | ✅ |
| `SENDING` | "Sending..." | Primary | ❌ |
| `SENT` | "Sent ✓" | Green | ❌ |
| `FAILED` | "Retry" | Error/red | ✅ |

---

## Simplified UI Models

These are derived from API responses and are what the UI works with. They contain only the fields needed for display and selection.

```kotlin
data class FireflyCategory(val id: String, val name: String)
data class FireflyTag(val id: String, val name: String)  // name ← attributes.tag
data class FireflyBudget(val id: String, val name: String)
data class FireflyAccount(val id: String, val name: String, val type: String)
```

---

## API Request Models

### `FireflyTransactionRequest`

Top-level POST body for `POST /api/v1/transactions`.

```kotlin
data class FireflyTransactionRequest(
    @SerializedName("error_if_duplicate_hash")
    val errorIfDuplicate: Boolean = false,   // Don't reject duplicate transactions

    @SerializedName("apply_rules")
    val applyRules: Boolean = true,          // Let Firefly apply automation rules

    val transactions: List<FireflyTransactionSplit>  // Always a list of 1 split
)
```

### `FireflyTransactionSplit`

The individual transaction data. All optional fields are `null` when not set by the user.

```kotlin
data class FireflyTransactionSplit(
    val type: String,                       // "withdrawal" or "deposit"
    val description: String,                // User-edited or auto-generated from SMS
    val amount: String,                     // "2500.00" — always 2 decimal places, US locale

    @SerializedName("source_id")
    val sourceId: String?,                  // Asset account ID for withdrawals

    @SerializedName("destination_id")
    val destinationId: String?,             // Asset account ID for deposits

    @SerializedName("source_name")
    val sourceName: String?,                // Free-text revenue account name for deposits

    @SerializedName("destination_name")
    val destinationName: String?,           // Free-text expense account name for withdrawals

    val date: String,                       // "2025-01-15T10:30:00+05:30"

    val notes: String?,                     // Contains "Auto-parsed from SMS:\n[body]"

    @SerializedName("category_name")
    val categoryName: String?,              // Selected category name

    val tags: List<String>?,               // Selected tag names

    @SerializedName("budget_id")
    val budgetId: String?                   // Selected budget ID
)
```

---

## API Response Models

### Account Response

```
FireflyAccountsResponse
  └── data: List<FireflyAccountWrapper>
        ├── id: String
        └── attributes: FireflyAccountAttributes
                ├── name: String
                ├── type: String
                ├── accountNumber: String?   (JSON: "account_number")
                └── currentBalance: String?  (JSON: "current_balance")
```

### Category Response

```
FireflyCategoriesResponse
  └── data: List<FireflyCategoryWrapper>
        ├── id: String
        └── attributes: FireflyCategoryAttributes
                └── name: String
```

### Tag Response

```
FireflyTagsResponse
  └── data: List<FireflyTagWrapper>
        ├── id: String
        └── attributes: FireflyTagAttributes
                └── tag: String   ← mapped to FireflyTag.name in the UI
```

### Transaction Create Response

```kotlin
data class FireflyTransactionResponse(
    val data: FireflyTransactionData?   // null if error body doesn't match
)

data class FireflyTransactionData(
    val id: String,      // The newly created transaction ID
    val type: String     // Always "transactions"
)
```

---

## `DebugLog.Entry`

```kotlin
data class Entry(
    val timestamp: String,   // "HH:mm:ss.SSS" — formatted at time of log() call
    val tag: String,         // Source component: "SmsParser", "HTTP", "SmsReceiver", etc.
    val message: String      // Log message
)
```

Entries are stored newest-first. The backing list is capped at **200 entries** to prevent unbounded memory growth.

---

## Model Relationships

```
FireflyCategory ──────────────────────▼
FireflyTag ───────────────────────────► ParsedTransaction
FireflyBudget ───────────────────────▼  (via SmsParser.parse)
FireflyAccount ──────────────────────▼          │
SmsMessage ────(SmsParser.parse)───────▲          │
                                                       │
                             (TransactionViewModel or SmsReceiver)
                                                       │
                                                       ▼
                                           FireflyTransactionSplit
                                                       │
                                                       ▼
                                           FireflyTransactionRequest
                                           (transactions: list of 1)
                                                       │
                                       POST /api/v1/transactions
                                                       │
                                                       ▼
                                           FireflyTransactionResponse
                                           └── data.id: String (created tx ID)
```

The `ParsedTransaction` is the central hub — it starts from a parsed SMS and accumulates Firefly metadata from `FireflyDataViewModel` before being converted into a `FireflyTransactionSplit` for the API call.
