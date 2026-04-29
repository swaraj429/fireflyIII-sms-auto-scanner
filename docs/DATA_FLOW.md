# 🔄 Data Flow

This document traces the exact path data takes through the system for every major user action. Each flow is self-contained — you can read only the one you need.

---

## Flow 1: App First Launch & Configuration

```mermaid
sequenceDiagram
    actor User
    participant MA as MainActivity
    participant FA as FireflyApp
    participant NH as NotificationHelper
    participant SS as SetupScreen
    participant SVM as SetupViewModel
    participant AP as AppPrefs
    participant RC as RetrofitClient
    participant FF as Firefly III API

    Note over FA: Application.onCreate()
    FA->>NH: createChannel("firefly_transactions")
    NH-->>FA: Channel created (idempotent)

    FA->>MA: Activity created
    MA->>SS: Compose renders SetupScreen

    SS->>SVM: Read initial state (baseUrl, accessToken, accountId)
    SVM->>AP: prefs.baseUrl, prefs.accessToken, prefs.accountId
    AP-->>SVM: Stored values (empty on first run)
    SVM-->>SS: Display in text fields

    User->>SS: Types Base URL, Token, Account ID
    SS->>SVM: Updates baseUrl / accessToken / accountId mutableStateOf

    User->>SS: Taps "🔌 Test Connection"
    SS->>SVM: testConnection()
    SVM->>SVM: isTesting = true (shows spinner)
    SVM->>RC: create(baseUrl, accessToken)
    RC-->>SVM: FireflyApi instance

    SVM->>FF: GET /api/v1/about
    FF-->>SVM: 200 OK { version: "6.1.13" }

    SVM->>AP: saveConfig() → writes baseUrl, accessToken, accountId
    SVM->>SVM: connectionStatus = "✅ Connected! Firefly v6.1.13"
    SVM->>SVM: isTesting = false
    SVM-->>SS: Recompose → green status card
```

**Key points:**
- `testConnection()` runs inside `viewModelScope.launch {}` — it's a suspend call on `Dispatchers.Main` that calls a `suspend fun` inside
- `saveConfig()` is only called on **successful** connection test, not on manual "Save" (which can be triggered independently)
- The `isTesting` flag controls a `CircularProgressIndicator` — mutations are main-thread safe because they happen inside the coroutine body which executes on `Dispatchers.Main` by default

---

## Flow 2: Manual SMS Scan → Parse → Submit

```mermaid
sequenceDiagram
    actor User
    participant SL as SmsListScreen
    participant SMSVM as SmsViewModel
    participant SR as SmsReader
    participant CR as ContentResolver
    participant SP as SmsParser
    participant TS as TransactionScreen
    participant FDVM as FireflyDataViewModel
    participant TVM as TransactionViewModel
    participant RC as RetrofitClient
    participant FF as Firefly III API

    User->>SL: Selects date range (e.g. last 7 days)
    SL->>SMSVM: fromDate = X, toDate = Y (mutableStateOf)

    User->>SL: Taps "📥 Scan SMS"
    SL->>SMSVM: loadSmsByDateRange()
    SMSVM->>SR: readMessagesByDateRange(contentResolver, from, to)
    SR->>CR: query("content://sms/inbox", selection="DATE >= ? AND DATE <= ?")
    CR-->>SR: Cursor with N rows
    SR-->>SMSVM: List<SmsMessage>
    SMSVM->>SMSVM: smsMessages = List<SmsMessage>
    SMSVM-->>SL: Recompose → shows N cards

    User->>SL: Taps "⚡ Parse & View Transactions →"
    SL->>SMSVM: parseMessages()
    SMSVM->>SP: parseAll(smsMessages)

    loop For each SmsMessage
        SP->>SP: extractAmount() — tries 3 regex patterns
        SP->>SP: determineType() — keyword scan
        SP-->>SMSVM: ParsedTransaction or null
    end

    SMSVM->>SMSVM: parsedTransactions = List<ParsedTransaction>
    SL->>TS: navController.navigate("transactions")

    Note over TS: User wants to enrich before submitting
    User->>TS: Taps "Sync" button
    TS->>FDVM: refreshAll()

    par Parallel fetches (sequential in implementation)
        FDVM->>FF: GET /api/v1/categories
        FDVM->>FF: GET /api/v1/tags
        FDVM->>FF: GET /api/v1/budgets
        FDVM->>FF: GET /api/v1/accounts?type=asset
        FDVM->>FF: GET /api/v1/accounts?type=expense
        FDVM->>FF: GET /api/v1/accounts?type=revenue
    end

    FDVM->>FDVM: Populate categories, tags, budgets, assetAccounts, etc.
    FDVM-->>TS: hasSynced = true → dropdowns appear

    User->>TS: Selects category, budget, tags, accounts for a transaction
    TS->>TS: Updates transaction.categoryName, budgetId, selectedTags, etc.

    User->>TS: Taps "Send to Firefly" on a card
    TS->>TVM: sendTransaction(transaction, onComplete)
    TVM->>RC: create(prefs.baseUrl, prefs.accessToken)
    TVM->>FF: POST /api/v1/transactions\n{ type, description, amount, source_id, category_name, tags, budget_id, ... }
    FF-->>TVM: 200 OK { data: { id: "42" } }
    TVM->>TVM: transaction.status = SENT\nlastResult = "✅ Created transaction #42"
    TVM-->>TS: Recompose → green "Sent ✓" button
```

---

## Flow 3: Live SMS → Notification → Auto-Send

```mermaid
sequenceDiagram
    participant OS as Android OS
    participant RECV as SmsReceiver\n(BroadcastReceiver)
    participant SP as SmsParser
    participant DBG as DebugLog
    participant NH as NotificationHelper
    participant NM as NotificationManager
    actor User
    participant RC as RetrofitClient
    participant FF as Firefly III API

    OS->>RECV: onReceive(SMS_RECEIVED_ACTION)
    RECV->>RECV: getMessagesFromIntent(intent)
    RECV->>DBG: log("SMS received from HDFCBK...")

    RECV->>SP: parse(SmsMessage)
    SP->>SP: extractAmount() → ₹2500.00
    SP->>SP: determineType() → DEBIT
    SP-->>RECV: ParsedTransaction(amount=2500, type=DEBIT)

    RECV->>NH: showTransactionNotification(transaction, notifId=1001)
    NH->>NH: Build review PendingIntent (to MainActivity + extras)
    NH->>NH: Build sendNow PendingIntent (to SmsReceiver)
    NH->>NH: Build dismiss PendingIntent (to SmsReceiver)
    NH->>NM: notify(1001, notification)
    NM-->>User: 🔔 "🔴 Debit — ₹2,500.00 | ⚡ Send | ✋ Dismiss"

    alt User taps "⚡ Send to Firefly"
        User->>RECV: onReceive(ACTION_SEND_NOW)
        RECV->>RECV: Extract extras (amount, type, rawMessage...)
        RECV->>RECV: goAsync() → pendingResult
        RECV->>RC: create(prefs.baseUrl, prefs.accessToken)
        RECV->>FF: POST /api/v1/transactions
        FF-->>RECV: 200 OK { id: "43" }
        RECV->>NM: cancel(1001)
        RECV->>NM: notify(1002, "✅ ₹2500 debit added to Firefly (#43)")
        RECV->>RECV: pendingResult.finish()

    else User taps notification body (Review)
        User->>MA: MainActivity.onNewIntent(ACTION_REVIEW_TRANSACTION)
        Note right of MA: see Flow 4
    end
```

---

## Flow 4: Notification Tap → Edit in App

```mermaid
sequenceDiagram
    actor User
    participant NM as NotificationManager
    participant MA as MainActivity
    participant SMSVM as SmsViewModel
    participant NC as NavController
    participant TS as TransactionScreen

    User->>NM: Taps notification body
    NM->>MA: Deliver Intent (action=ACTION_REVIEW_TRANSACTION)\nextras: amount, type, sender, rawMessage, timestamp

    alt App not running
        MA->>MA: onCreate(intent)
        MA->>MA: handleNotificationIntent(intent)
    else App already running
        MA->>MA: onNewIntent(intent)
        MA->>MA: handleNotificationIntent(intent)
    end

    MA->>MA: Reconstruct ParsedTransaction from extras
    MA->>MA: pendingNotificationTransaction.value = transaction

    Note over MA: In Compose, LaunchedEffect observes this state
    MA->>SMSVM: addTransactionFromNotification(transaction)
    SMSVM->>SMSVM: Check for duplicate (same timestamp + amount)
    SMSVM->>SMSVM: parsedTransactions.add(0, transaction)  ← prepend to top
    SMSVM->>SMSVM: statusMessage = "📩 New transaction from notification"

    MA->>NC: navigate("transactions")\n{ launchSingleTop = true }
    NC->>TS: TransactionScreen renders
    TS->>SMSVM: Reads parsedTransactions
    TS-->>User: Shows new transaction at top of list, ready to edit & submit
```

---

## Flow 5: DebugLog — Multi-Thread Safety

This flow explains how `DebugLog` receives log calls from background threads (OkHttp interceptors, `SmsReceiver` coroutines) without crashing the Compose snapshot system.

```mermaid
sequenceDiagram
    participant OKH as OkHttp Thread\n(background)
    participant MAIN as Main Thread
    participant DBG as DebugLog
    participant COWL as CopyOnWriteArrayList\n(_entries)
    participant CSL as mutableStateListOf\n(entries — Compose)
    participant DS as DebugScreen

    OKH->>DBG: log("HTTP", "← 200 /api/v1/about")
    DBG->>COWL: _entries.add(0, Entry(...))  ← Thread-safe write
    DBG->>DBG: while (_entries.size > 200) remove last

    DBG->>MAIN: Handler.post { ... }  ← Switch to main thread

    Note over MAIN: Runs on main thread
    MAIN->>CSL: entries.clear() + entries.addAll(_entries)
    CSL->>DS: Compose recomposition triggered
    DS-->>User: New log entry appears in Debug tab
```

**Why two lists?** The `CopyOnWriteArrayList` (`_entries`) acts as the truth — it's safe to write from any thread. The `mutableStateListOf` (`entries`) is the Compose-observable view — it's only ever written from the main thread. The `postToMain` helper checks `Looper.myLooper()` so if the call already originates from the main thread (e.g. from a ViewModel), the `Handler.post` overhead is skipped.

---

## State Ownership Map

| State | Owner | How UI reads it |
|---|---|---|
| `baseUrl`, `accessToken`, `accountId` | `SetupViewModel` | `mutableStateOf` (observable by delegation) |
| `connectionStatus`, `isTesting` | `SetupViewModel` | `mutableStateOf` |
| `smsMessages` | `SmsViewModel` | `mutableStateListOf` |
| `parsedTransactions` | `SmsViewModel` | `mutableStateListOf` |
| `fromDate`, `toDate` | `SmsViewModel` | `mutableStateOf` |
| `lastResult` | `TransactionViewModel` | `mutableStateOf` |
| `categories`, `tags`, `budgets`, `*Accounts` | `FireflyDataViewModel` | `mutableStateListOf` |
| `hasSynced`, `isLoading`, `lastSyncStatus` | `FireflyDataViewModel` | `mutableStateOf` |
| `entries`, `lastRequest`, `lastResponse` | `DebugLog` (singleton) | `mutableStateListOf` / `mutableStateOf` |
| `pendingNotificationTransaction` | `MainActivity` | `mutableStateOf` (passed to `MainApp`) |
