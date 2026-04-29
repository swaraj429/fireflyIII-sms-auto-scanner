# 🏛️ Architecture Overview

This document explains the system design of Firefly III SMS Scanner — how the layers relate to each other, what responsibilities each component has, and why key design decisions were made.

---

## The Big Picture

The app has two independent operational modes that share the same ViewModel layer:

1. **Manual mode** — user opens the app, scans SMS inbox by date range, reviews parsed transactions, enriches them with Firefly metadata, and submits
2. **Live mode** — a background `BroadcastReceiver` intercepts every incoming SMS, parses it on the spot, and notifies the user without the app being open

```mermaid
graph TB
    subgraph DEVICE["📱 Android Device"]
        subgraph LIVE["Live Mode (Background)"]
            SMS_IN["📩 Incoming SMS"] --> RECV["SmsReceiver\n(BroadcastReceiver)"]
            RECV --> PARSER["SmsParser"]
            PARSER --> NOTIF["NotificationHelper"]
            NOTIF --> NOTIF_UI["🔔 Notification\n(Send Now / Review)"]
        end

        subgraph APP["App (Foreground)"]
            subgraph UI_LAYER["UI Layer (Jetpack Compose)"]
                SETUP["SetupScreen"]
                SMS_LIST["SmsListScreen"]
                TXN["TransactionScreen"]
                DEBUG["DebugScreen"]
            end

            subgraph VM_LAYER["ViewModel Layer"]
                SETUP_VM["SetupViewModel"]
                SMS_VM["SmsViewModel"]
                TXN_VM["TransactionViewModel"]
                DATA_VM["FireflyDataViewModel"]
            end

            subgraph DATA_LAYER["Data Layer"]
                SMS_READER["SmsReader"]
                RETROFIT["RetrofitClient\n+ FireflyApi"]
                PREFS["AppPrefs\n(SharedPreferences)"]
                DBG["DebugLog"]
            end
        end

        subgraph FIREFLY["☁️ Firefly III Instance"]
            API["REST API\n/api/v1/..."]
        end
    end

    NOTIF_UI -- "Tap: Review" --> APP
    NOTIF_UI -- "Tap: Send Now" --> RECV
    RECV -- "goAsync + coroutine" --> RETROFIT

    UI_LAYER <--> VM_LAYER
    VM_LAYER --> DATA_LAYER
    DATA_LAYER --> RETROFIT
    RETROFIT --> API
    SMS_READER --> SMS_LIST
    PREFS --> SETUP_VM
    DBG --> DEBUG
```

---

## Layered Architecture

The project follows **MVVM (Model-View-ViewModel)** strictly. Here's what each layer is and is not allowed to do:

```mermaid
graph LR
    subgraph VIEW["View Layer\n(Compose UI)"]
        direction TB
        V1["SetupScreen.kt"]
        V2["SmsListScreen.kt"]
        V3["TransactionScreen.kt"]
        V4["DebugScreen.kt"]
    end

    subgraph VIEWMODEL["ViewModel Layer"]
        direction TB
        VM1["SetupViewModel"]
        VM2["SmsViewModel"]
        VM3["TransactionViewModel"]
        VM4["FireflyDataViewModel"]
    end

    subgraph MODEL["Model / Data Layer"]
        direction TB
        M1["ParsedTransaction\nSmsMessage\nFireflyModels"]
        M2["SmsReader"]
        M3["SmsParser"]
        M4["RetrofitClient\nFireflyApi"]
        M5["AppPrefs"]
        M6["DebugLog"]
    end

    VIEW -- "reads state\ncalls functions" --> VIEWMODEL
    VIEWMODEL -- "launches coroutines\nreads/writes" --> MODEL
    VIEW -. "❌ never directly\naccesses" .-> MODEL
```

### Rules enforced in this codebase

| Rule | Reason |
|---|---|
| Screens never import `SmsReader`, `RetrofitClient`, etc. | Keeps UI layer thin and testable |
| ViewModels never import Compose (`@Composable`) | Prevents VM from being tied to the UI lifecycle |
| All Compose `mutableStateOf` mutations happen on the main thread | Prevents `IllegalStateException` from Compose snapshot system |
| `BroadcastReceiver` uses `goAsync()` for all network work | Android kills the receiver after `onReceive()` returns without it |

---

## Component Inventory

### Application Class — `FireflyApp.kt`

The custom `Application` subclass is registered in `AndroidManifest.xml` via `android:name=".FireflyApp"`. It runs before any Activity or BroadcastReceiver.

**Responsibilities:**
- Creates the `"firefly_transactions"` notification channel on startup
- This is the only safe place to create a notification channel — it's idempotent and must be done before any notification is shown

### Entry Points

| Component | Class | Trigger |
|---|---|---|
| Main UI | `MainActivity` | User launches app, or taps "Review" on notification |
| SMS listener | `SmsReceiver` | Android dispatches `SMS_RECEIVED_ACTION` broadcast |
| Notification actions | `SmsReceiver` | User taps "Send Now" or "Dismiss" on a notification |

---

## Screen Navigation

The app uses **Navigation Compose** with a single bottom navigation bar. Screens are simple destinations — no nested graphs, no deep links (except from the notification).

```mermaid
stateDiagram-v2
    [*] --> Setup : App launch (first run)
    Setup --> SmsList : Bottom bar
    SmsList --> Transactions : "Parse & View →" button
    Transactions --> SmsList : Bottom bar
    Setup --> Debug : Bottom bar
    SmsList --> Debug : Bottom bar
    Transactions --> Debug : Bottom bar

    note right of Transactions
        Also reached directly
        by tapping a notification
        (skips SMS scan step)
    end note
```

### Notification → App Navigation

When a transaction notification is tapped:

1. `NotificationHelper` creates a `PendingIntent` targeting `MainActivity` with `action = ACTION_REVIEW_TRANSACTION` and all transaction data as Intent extras
2. If the app is **not running** → `MainActivity.onCreate()` is called, which calls `handleNotificationIntent()`
3. If the app is **already running** → `MainActivity.onNewIntent()` is called, which also calls `handleNotificationIntent()`
4. `handleNotificationIntent()` reconstructs a `ParsedTransaction` from the extras and stores it in `pendingNotificationTransaction: MutableState<ParsedTransaction?>`
5. A `LaunchedEffect` in `MainApp()` observes this state — when non-null, it calls `smsViewModel.addTransactionFromNotification(tx)` and navigates to the Transactions tab

This approach avoids any singleton, static variable, or `Intent` passing to a ViewModel (which is an anti-pattern).

---

## ViewModel Ownership

All ViewModels are created by `viewModel()` in `MainApp()`, which means they're scoped to the **Activity** lifecycle. This is intentional — the same `SmsViewModel` instance is shared across the SMS tab and Transaction tab, so parsed transactions persist through tab navigation.

```mermaid
graph TD
    ACT["MainActivity\n(Activity scope)"] --> MAINAPP["MainApp()\nComposable"]
    MAINAPP --> SVM["SmsViewModel\n(shared)"]
    MAINAPP --> TVM["TransactionViewModel\n(shared)"]
    MAINAPP --> STVM["SetupViewModel\n(shared)"]
    MAINAPP --> FDVM["FireflyDataViewModel\n(shared)"]

    MAINAPP --> SETUP_SCR["SetupScreen\nreads: SetupViewModel"]
    MAINAPP --> SMS_SCR["SmsListScreen\nreads: SmsViewModel"]
    MAINAPP --> TXN_SCR["TransactionScreen\nreads: SmsViewModel\n        TransactionViewModel\n        FireflyDataViewModel"]
    MAINAPP --> DBG_SCR["DebugScreen\nreads: DebugLog (singleton)"]
```

---

## Dependency Graph (simplified)

```mermaid
graph LR
    SmsReceiver --> SmsParser
    SmsReceiver --> NotificationHelper
    SmsReceiver --> RetrofitClient
    SmsReceiver --> AppPrefs

    SetupViewModel --> RetrofitClient
    SetupViewModel --> AppPrefs

    SmsViewModel --> SmsReader
    SmsViewModel --> SmsParser

    TransactionViewModel --> RetrofitClient
    TransactionViewModel --> AppPrefs

    FireflyDataViewModel --> RetrofitClient
    FireflyDataViewModel --> AppPrefs

    RetrofitClient --> FireflyApi
    RetrofitClient --> DebugLog

    SmsParser --> DebugLog
    SmsReader --> DebugLog
```

All network calls go through `RetrofitClient.create()`. It is **not** a singleton — it's recreated on demand with the current `baseUrl` and `accessToken` from `AppPrefs`. This means config changes take effect immediately without restarting.

---

## Key Design Decisions

### Why not use a Service for live SMS detection?

Android 8+ (API 26) severely restricts background services for apps not in the foreground. A `BroadcastReceiver` is the correct Android primitive for reacting to system events like `SMS_RECEIVED`. The receiver is:
- Declared in `AndroidManifest.xml` (not registered dynamically)
- Stateless — it creates nothing persistent, just posts a notification
- Extended with `goAsync()` to safely perform a network call on a coroutine without being killed

### Why is `DebugLog` a global singleton and not a ViewModel?

`DebugLog` needs to receive log entries from `RetrofitClient` interceptors running on OkHttp's background threads, from `SmsReceiver` (which has no ViewModel), and from all ViewModels — before any screen is open. A ViewModel's lifecycle is too short and too narrowly scoped. The singleton is safe because:
- Writes to the backing list use `CopyOnWriteArrayList` (thread-safe)
- Mutations to Compose observable state (`mutableStateListOf`) are always posted to the main thread via `Handler(Looper.getMainLooper())`

### Why is `RetrofitClient` not a singleton?

The base URL and access token can be changed at any time in the Setup screen. Making `RetrofitClient` a singleton would require an invalidation/reset mechanism. Instead, a new client is cheaply constructed per-call. OkHttp connection pooling is not lost because Android's default DNS and connection pool operates at the OS level.

### Why SharedPreferences and not DataStore?

The project deliberately keeps its dependency footprint minimal. `AppPrefs` is a thin wrapper around `SharedPreferences` with synchronous reads and async (`apply()`) writes — suitable for the simple key-value config this app stores. Migration to `DataStore` is a tracked roadmap item.
