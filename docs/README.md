# 📚 Firefly III SMS Scanner — Documentation

Welcome to the technical documentation hub. This is the right place if you want to understand how the project works in depth, contribute code, or extend it.

---

## 📖 Documents

| Document | What it covers |
|----------|----------------|
| [Architecture Overview](ARCHITECTURE.md) | System design, component map, layered architecture |
| [Data Flow](DATA_FLOW.md) | End-to-end journeys for every major user action |
| [SMS Parsing Engine](SMS_PARSING.md) | How bank SMS messages are detected and parsed |
| [Notification System](NOTIFICATION_SYSTEM.md) | Live SMS detection, BroadcastReceiver, notification actions |
| [Firefly III Integration](FIREFLY_INTEGRATION.md) | API client, authentication, all endpoints used |
| [Data Models](DATA_MODELS.md) | Every data class and its fields explained |
| [Threading Model](THREADING.md) | Coroutines, main thread rules, DebugLog internals |

---

## Quick Reference

### Tech Stack at a Glance

```
Language:       Kotlin 1.9.22
UI:             Jetpack Compose + Material3 (BOM 2024.02.00)
Architecture:   MVVM (ViewModel + StateFlow/mutableState)
Networking:     Retrofit 2.9 + OkHttp 4.12
Navigation:     Navigation Compose 2.7.6
Background:     BroadcastReceiver + goAsync() + Coroutines
Persistence:    SharedPreferences
Min SDK:        API 26 (Android 8.0)
Target SDK:     API 34 (Android 14)
```

### Key Entry Points

| Starting point | File |
|---|---|
| App bootstrap | `FireflyApp.kt` |
| Main screen host | `MainActivity.kt` |
| Navigation graph | `MainApp()` in `MainActivity.kt` |
| SMS receive | `SmsReceiver.kt` |
| Transaction parsing | `SmsParser.kt` |
| Firefly API calls | `FireflyApi.kt` + `RetrofitClient.kt` |
