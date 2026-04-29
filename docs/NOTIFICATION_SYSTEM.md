# 🔔 Notification System & Live SMS Detection

This document explains how the app listens for incoming SMS in real-time, parses them without any user interaction, and delivers actionable notifications — all while the app may not be running at all.

---

## The Core Component: `SmsReceiver`

`SmsReceiver` is a `BroadcastReceiver` registered in `AndroidManifest.xml`. It is **not** a Service — it has no persistent lifecycle. Android instantiates it, calls `onReceive()`, and the instance is destroyed immediately after the method returns.

```
[Installed] ──(App installed)────────────────► Dormant

Dormant ──(SMS_RECEIVED broadcast)───────────► Receiving ──► Dormant
Dormant ──(ACTION_SEND_NOW broadcast)────────► Receiving ──► Dormant
Dormant ──(ACTION_DISMISS broadcast)─────────► Receiving ──► Dormant
```

The receiver handles **three distinct actions**:

| `intent.action` | Triggered when |
|---|---|
| `android.provider.Telephony.SMS_RECEIVED` | Any SMS arrives on the device |
| `com.swaraj429.firefly3smsscanner.ACTION_SEND_NOW` | User taps "⚡ Send to Firefly" on a notification |
| `com.swaraj429.firefly3smsscanner.ACTION_DISMISS` | User taps "✋ Dismiss" on a notification |

---

## Live SMS Detection Flow

```
📩 SMS arrives on device
         │
         ▼
Android dispatches SMS_RECEIVED broadcast
         │
         ▼
SmsReceiver.onReceive()
         │
         ▼
getMessagesFromIntent(intent) → Array<SmsMessage>
         │
         ├─ null or empty? ────────────────────► return (no-op)
         │
         └─ For each SmsMessage:
                  │
                  ▼
         Extract sender + body + timestamp
                  │
                  ▼
         SmsParser.parse(SmsMessage)
                  │
                  ├─ null (not a transaction) ──────► log “skipping” → continue loop
                  │
                  └─ ParsedTransaction found
                           │
                           ▼
                  NotificationHelper.showTransactionNotification()
                           │
                           ▼
                  🔔 Notification shown with action buttons
```

### Why `getMessagesFromIntent()`?

A single SMS can arrive in multiple parts (concatenated SMS over 160 characters). The Android OS delivers them as an array of `SmsMessage` objects inside a single `SMS_RECEIVED` intent. `Telephony.Sms.Intents.getMessagesFromIntent()` is the official API to extract and reassemble them.

---

## The `goAsync()` Pattern

`BroadcastReceiver.onReceive()` runs on the **main thread** and has a hard 10-second timeout enforced by Android. After `onReceive()` returns, any background threads spawned inside it can be killed.

For the "Send Now" action, we need to make a network call (which can take several seconds). The solution:

```kotlin
val pendingResult = goAsync()       // 1. Tell Android: "I'm not done yet"

CoroutineScope(Dispatchers.IO).launch {
    try {
        // ... network call to Firefly III ...
    } finally {
        pendingResult.finish()       // 2. Tell Android: "Now I'm done"
    }
}
```

`goAsync()` returns a `PendingResult` object. Android will not consider the broadcast complete until `pendingResult.finish()` is called. This extends the timeout to approximately 60 seconds.

**Step-by-step:**

```
1. onReceive() called on Main Thread  [10s timeout starts]
         │
2.       val pendingResult = goAsync()   ← tells Android: "wait, I'm not done"
         │
3.       CoroutineScope(Dispatchers.IO).launch { ... }
         │
4. onReceive() returns  ← Android is waiting (pendingResult not finished)
         │
         └────── [IO Thread] ─────────────────────────────────────────────►
                                                                          │
5.                                            POST /api/v1/transactions ←─┘
                                                          │
6.                                            pendingResult.finish() ← broadcast complete ✓
```

---

## Notification Structure

Each transaction notification has:

```
┌─────────────────────────────────────────────┐
│ 🔴 Debit Detected — ₹2,500.00               │  ← Title
│ From VD-HDFCBK: INR 2,500.00 debited from  │  ← Body (BigTextStyle expands on tap)
│ a/c **1234 on 15-Jan-25 at POS AMAZON...    │
│                                             │
│  ⚡ Send to Firefly    ✋ Dismiss            │  ← Action buttons
└─────────────────────────────────────────────┘
```

All three tap targets (notification body, Send button, Dismiss button) use `PendingIntent` with `FLAG_IMMUTABLE`:

| Tap target | PendingIntent type | Destination |
|---|---|---|
| Notification body | `getActivity()` | `MainActivity` with `ACTION_REVIEW_TRANSACTION` + extras |
| ⚡ Send to Firefly | `getBroadcast()` | `SmsReceiver` with `ACTION_SEND_NOW` + extras |
| ✋ Dismiss | `getBroadcast()` | `SmsReceiver` with `ACTION_DISMISS` |

### PendingIntent request codes

Each notification uses a unique `notificationId` (auto-incremented from 1000). To avoid PendingIntent collisions between notifications:
- Review intent: `requestCode = notificationId`
- Send intent: `requestCode = notificationId + 10000`
- Dismiss intent: `requestCode = notificationId + 20000`

---

## Data Passed via Intent Extras

All transaction data is serialised as simple primitive extras (no `Parcelable` or `Serializable` needed):

| Extra key | Type | Value |
|---|---|---|
| `extra_amount` | `Double` | Parsed transaction amount |
| `extra_type` | `String` | `"DEBIT"`, `"CREDIT"`, or `"UNKNOWN"` |
| `extra_sender` | `String` | SMS sender address |
| `extra_raw_message` | `String` | Full original SMS body |
| `extra_timestamp` | `Long` | `smsMessage.timestampMillis` |
| `extra_notification_id` | `Int` | The notification ID (for auto-cancel) |

### Why not pass a serialised object?

`ParsedTransaction` is not `Parcelable`. Adding `@Parcelize` would be fine for an Activity-to-Activity case, but for PendingIntents that survive the process being killed, serialised objects are fragile (class versions can mismatch). Primitive extras are always safe.

---

## Notification Channel

The notification channel `"firefly_transactions"` is created in `FireflyApp.onCreate()`:

```kotlin
NotificationChannel(
    CHANNEL_ID,                              // "firefly_transactions"
    "Transaction Alerts",                    // User-visible name
    NotificationManager.IMPORTANCE_HIGH      // Makes heads-up / peek-through
).apply {
    description = "Alerts for detected transaction SMS messages"
    enableLights(true)
    enableVibration(true)
}
```

`IMPORTANCE_HIGH` causes the notification to appear as a **heads-up notification** (floating banner) even when the user is in another app. This is intentional — a transaction SMS should never be missed.

**Channel creation is idempotent**: calling `createNotificationChannel()` with the same ID multiple times is safe and has no effect if the channel already exists.

---

## Permission Requirements

| Permission | Android API level | Required for |
|---|---|---|
| `RECEIVE_SMS` | All | `SMS_RECEIVED` broadcast to be delivered |
| `READ_SMS` | All | Reading SMS inbox (manual scan) |
| `POST_NOTIFICATIONS` | API 33+ (Android 13+) | Showing any notification |
| `BROADCAST_SMS` | System only | Protects the receiver from spoofed broadcasts |

### `android:permission="android.permission.BROADCAST_SMS"` on the receiver

In `AndroidManifest.xml`, the receiver is declared with:
```xml
<receiver
    android:name=".notification.SmsReceiver"
    android:exported="true"
    android:permission="android.permission.BROADCAST_SMS">
```

This means only the Android system (which holds `BROADCAST_SMS`) can send the `SMS_RECEIVED` intent to this receiver. A malicious third-party app cannot spoof incoming SMS broadcasts.

---

## Requesting Permissions at Runtime

The app requests permissions in two places:

1. **`POST_NOTIFICATIONS` (Android 13+)**: Requested automatically in a `LaunchedEffect(Unit)` in `MainApp()` when the app is first opened.

2. **`READ_SMS` + `RECEIVE_SMS`**: Requested together when the user taps "📥 Scan SMS" on the SMS tab (only if permission hasn't been granted). This is the right UX moment — right before the user needs the permission.

The `permissionLauncher` uses `RequestMultiplePermissions()` so both are requested in a single system dialog.

---

## Battery Optimisation Warning

On many Android OEMs (Samsung, Xiaomi, OnePlus, Oppo, Vivo), aggressive battery optimisation can prevent `BroadcastReceiver` from receiving broadcasts when the app is in the background.

Users should be directed to:
- **Settings → Apps → Firefly SMS Scanner → Battery → Unrestricted** (Samsung One UI)
- **Settings → Battery → Battery Saver → Firefly SMS Scanner → No restrictions** (Xiaomi MIUI)

This is a known Android fragmentation issue not solvable by the app itself.

---

## Result Notifications (Send Now flow)

After auto-sending from a notification action, a result notification is shown:

```
Success:
┌─────────────────────────────────────┐
│ Firefly — Transaction Added ✓       │
│ ✅ ₹2500.00 debit added to          │
│ Firefly (#43)                       │
└─────────────────────────────────────┘

Failure:
┌─────────────────────────────────────┐
│ Firefly — Send Failed               │
│ ❌ HTTP 422: The amount field is    │
│ required.                           │
└─────────────────────────────────────┘
```

Result notifications use `PRIORITY_DEFAULT` (not `HIGH`), so they don't interrupt the user with a floating banner.
