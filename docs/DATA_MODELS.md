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

### `ParsedTransaction`

The central in-memory model of the app. Starts from a parsed SMS and accumulates user-selected Firefly metadata before being submitted or updated.

```kotlin
data class ParsedTransaction(
    // ── Parsed from SMS ──────────────────────────────────────────────────
    val amount: Double,              // Raw parsed amount
    val type: TransactionType,       // WITHDRAWAL, DEPOSIT, or TRANSFER
    val rawMessage: String,          // Original SMS body — shown in Debug and as notes
    val sender: String = "",         // SMS sender address
    val timestamp: Long = System.currentTimeMillis(), // Original SMS timestamp
    var paymentMode: String? = null, // "UPI", "Card", "ATM", "NetBanking"

    // ── User corrections ────────────────────────────────────────────────────
    var correctedAmount: Double? = null,
    var correctedType: TransactionType? = null,

    // ── Firefly metadata (user-selected in TransactionEditorSheet) ─────────
    var description: String = "",    // Prefilled with extracted payee, editable
    var categoryName: String? = null,
    var selectedTags: MutableList<String> = mutableListOf(),
    var budgetId: String? = null,
    var budgetName: String? = null,
    var sourceAccountId: String? = null,
    var sourceAccountName: String? = null,
    var destinationAccountId: String? = null,
    var destinationAccountName: String? = null,

    // ── Tracking & Sync ─────────────────────────────────────────────────────
    var status: SendStatus = SendStatus.PENDING,
    var dismissReason: DismissReason? = null,
    var dismissedAt: Long? = null,
    var fireflyTransactionId: String? = null,        // Firefly group ID
    var fireflyTransactionJournalId: String? = null, // Split journal ID (for PUT)
    var lastSyncedAt: Long? = null,
    var hasRemoteEdits: Boolean = false
)
```

**Computed properties:**

```kotlin
val effectiveAmount: Double get() = correctedAmount ?: amount
val effectiveType: TransactionType get() = correctedType ?: type
val isExpense: Boolean get() = effectiveType == TransactionType.WITHDRAWAL
```

---

### `TransactionType`

Aligned with Firefly III transaction types:

```kotlin
enum class TransactionType {
    WITHDRAWAL,  // Money leaving account (Expense) -> "withdrawal"
    DEPOSIT,     // Money entering account (Income) -> "deposit"
    TRANSFER;    // Between own accounts -> "transfer"

    fun toFireflyType(): String = name.lowercase()
    fun displayLabel(): String = when (this) {
        WITHDRAWAL -> "Expense"
        DEPOSIT -> "Income"
        TRANSFER -> "Transfer"
    }
}
```

---

### `SendStatus`

```kotlin
enum class SendStatus {
    PENDING,   // Transaction parsed but not yet submitted
    SENDING,   // Network call in progress (POST create or PUT update)
    SENT,      // Successfully posted/updated in Firefly III
    FAILED,    // Network or API error occurred
    DISMISSED  // User dismissed the transaction (e.g. duplicate or CC echo)
}
```

`SendStatus` drives the button label and action in `TransactionEditorSheet`:

| Status | Button label | Action | Enabled? |
|---|---|---|---|
| `PENDING` | "Save Transaction" | `POST /api/v1/transactions` | ✅ |
| `SENDING` | "Saving..." | In-flight request | ❌ |
| `SENT` | "Update in Firefly" | `PUT /api/v1/transactions/{id}` | ✅ |
| `FAILED` | "Retry" | Retry `POST` or `PUT` | ✅ |
| `DISMISSED` | "Save to Firefly" | Restore & `POST` to Firefly | ✅ |

---

### `DismissReason`

Tracks why a transaction was dismissed from the active inbox:

```kotlin
enum class DismissReason(
    val title: String,
    val description: String,
    val badgeLabel: String
) {
    DUPLICATE("Duplicate Transaction", "Same transaction reported by another SMS", "Duplicate"),
    CREDIT_CARD_ECHO("Credit Card Bill Echo", "Bank debit alert that mirrors CC bill payment", "CC Echo"),
    UNRELATED("Unrelated / Non-Expense", "Promotional, OTP, balance inquiry, or personal text", "Unrelated"),
    OTHER("Other", "Manually dismissed / ignored", "Dismissed");
}
```

---

---

## Local Database Entities (Room)

### `SmsRecordEntity`

Persisted record of every transaction SMS seen by the app (`sms_records` table). Uniquely indexed on `smsHash`.

```kotlin
@Entity(
    tableName = "sms_records",
    indices = [Index(value = ["smsHash"], unique = true)]
)
data class SmsRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsHash: String,                     // SHA-256(sender + body)
    val sender: String,                      // e.g. "VD-HDFCBK"
    val body: String,                        // Raw SMS text
    val smsTimestamp: Long,                  // Epoch millis
    val amount: Double,
    val transactionType: String,             // "WITHDRAWAL", "DEPOSIT", "TRANSFER"
    val description: String = "",

    // Mapped Firefly Metadata
    val sourceAccountId: String? = null,
    val sourceAccountName: String? = null,
    val destinationAccountId: String? = null,
    val destinationAccountName: String? = null,
    val categoryName: String? = null,
    val budgetId: String? = null,
    val budgetName: String? = null,
    val selectedTagsCommaSeparated: String = "",

    // Sync State
    val syncStatus: String = "PENDING",      // PENDING, SENT, FAILED, DISMISSED
    val fireflyTransactionId: String? = null,        // Group ID in Firefly
    val fireflyTransactionJournalId: String? = null, // Split journal ID (for PUT)
    val lastSyncedAt: Long? = null,

    // Remote Shadow (reconciliation)
    val remoteDescription: String? = null,
    val remoteTags: String? = null,
    val remoteCategory: String? = null,
    val hasLocalEdits: Boolean = false,

    // Dismissal
    val dismissReason: String? = null,       // DUPLICATE, CREDIT_CARD_ECHO, etc.
    val dismissedAt: Long? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

---

## Simplified UI Models

These are derived from API responses and are what the UI works with:

```kotlin
data class FireflyCategory(val id: String, val name: String)
data class FireflyTag(val id: String, val name: String)
data class FireflyBudget(val id: String, val name: String)
data class FireflyAccount(
    val id: String, 
    val name: String, 
    val type: String,
    val accountNumber: String? = null,
    val accountRole: String? = null
)
```

---

## API Request Models

### `FireflyTransactionRequest`

Top-level body for `POST /api/v1/transactions` and `PUT /api/v1/transactions/{id}`.

```kotlin
data class FireflyTransactionRequest(
    @SerializedName("error_if_duplicate_hash")
    val errorIfDuplicate: Boolean = false,

    @SerializedName("apply_rules")
    val applyRules: Boolean = true,

    val transactions: List<FireflyTransactionSplit>
)
```

### `FireflyTransactionSplit`

Individual split details. Required for both creation and updates:

```kotlin
data class FireflyTransactionSplit(
    val type: String,                       // "withdrawal" or "deposit"
    val description: String,                // Payee/merchant or custom description
    val amount: String,                     // "2500.00" — 2 decimal places, US locale
    @SerializedName("source_id") val sourceId: String? = null,
    @SerializedName("destination_id") val destinationId: String? = null,
    @SerializedName("source_name") val sourceName: String? = null,
    @SerializedName("destination_name") val destinationName: String? = null,
    val date: String,                       // ISO 8601 ("yyyy-MM-dd'T'HH:mm:ssXXX")
    val notes: String? = null,              // Body + "smsHash=<hash>" for reconciliation
    @SerializedName("category_name") val categoryName: String? = null,
    val tags: List<String>? = null,
    @SerializedName("budget_id") val budgetId: String? = null,
    @SerializedName("transaction_journal_id") val transactionJournalId: String? = null // Required for PUT
)
```

---

## API Response Models

### Account Response
```
FireflyAccountsResponse
  └── data: List<FireflyAccountWrapper>
        ├── id: String
        └── attributes: FireflyAccountAttributes (name, type, accountNumber, currentBalance)
```

### Category Response
```
FireflyCategoriesResponse
  └── data: List<FireflyCategoryWrapper>
        ├── id: String
        └── attributes: FireflyCategoryAttributes (name)
```

### Tag Response
```
FireflyTagsResponse
  └── data: List<FireflyTagWrapper>
        ├── id: String
        └── attributes: FireflyTagAttributes (tag)
```

### Transaction Create / Update Response
```kotlin
data class FireflyTransactionResponse(
    val data: FireflyTransactionData?
)
data class FireflyTransactionData(
    val id: String,      // Transaction group ID
    val type: String     // Always "transactions"
)
```

### Transaction List Response (Reconciliation)
```kotlin
data class FireflyTransactionListResponse(
    val data: List<FireflyTransactionGroupWrapper> = emptyList(),
    val meta: FireflyPaginationMeta? = null
)

data class FireflyTransactionGroupWrapper(
    val id: String,
    val attributes: FireflyTransactionGroupAttributes
)

data class FireflyTransactionGroupAttributes(
    val groupTitle: String? = null,
    val transactions: List<FireflyTransactionJournal> = emptyList()
)

data class FireflyTransactionJournal(
    val transactionJournalId: String = "",
    val type: String = "withdrawal",
    val description: String = "",
    val amount: String = "0.00",
    val date: String = "",
    val notes: String? = null,
    val categoryName: String? = null,
    val budgetId: String? = null,
    val tags: List<String>? = null,
    val sourceId: String? = null,
    val destinationId: String? = null,
    val destinationName: String? = null
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
