<div align="center">

# 🔥 Firefly III SMS Scanner

**Automatically detect transaction SMS messages and log them to your [Firefly III](https://www.firefly-iii.org/) instance — with one tap.**

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-Welcome-brightgreen.svg)](CONTRIBUTING.md)

*Stop manually logging every UPI payment, card swipe, and bank transfer.*

</div>

---

## 📸 Screenshots

> **Note for contributor:** Add screenshots in this order.

| Setup Screen | SMS Scanner | Transaction Editor |
|:---:|:---:|:---:|
| *[Add screenshot: Setup screen showing connection form + Live Detection Active card]* | *[Add screenshot: SMS tab with date range picker and scanned messages]* | *[Add screenshot: Transaction card with category/budget/tag dropdowns open]* |

| Notification Alert | Send Result |
|:---:|:---:|
| *[Add screenshot: Transaction notification with "⚡ Send to Firefly" button]* | *[Add screenshot: Success notification after auto-send]* |

---

## ✨ Features

### 📩 Real-Time SMS Detection
- Listens for incoming SMS **in the background** using a `BroadcastReceiver`
- Instantly notifies you when a transaction SMS is detected (Indian banking formats)
- Notification includes **"⚡ Send to Firefly"** (auto-submit) and **"Review & Edit"** (tap to open)

### 📅 Date-Range SMS Scanning
- Scan your entire SMS inbox for any custom date range — no 50-message cap
- Quick-select chips: **Today**, **3 Days**, **7 Days**, **30 Days**, **90 Days**
- Full Material3 date picker for precise control

### 🏦 Abacus-Style Transaction Editor
Before submitting, enrich each transaction with:
- **📁 Category** — searchable dropdown from your Firefly categories
- **💼 Budget** — optional budget assignment
- **🏦 Source Account** — your asset accounts
- **🏪 Destination Account** — expense/revenue accounts with free-text fallback
- **🏷️ Tags** — multi-select with checkboxes
- **Description** — editable, prefilled from SMS text

### 🔄 Firefly III Metadata Sync
- One-tap sync of all categories, tags, budgets, and accounts
- Cached in-session — no repeated API calls while reviewing

### 🐛 Built-in Debug Log
- Timestamped in-app log of every API call, SMS read, and parse decision
- Shareable plain-text export for issue reporting

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Details |
|---|---|
| **Android** | 8.0 (API 26) or higher |
| **Firefly III** | Self-hosted instance, accessible over your network |
| **Personal Access Token** | Generate at **Profile → OAuth → Personal Access Tokens** in Firefly III |

### Installation

#### Option A — Build from source (recommended for contributors)

```bash
# 1. Clone the repo
git clone https://github.com/swaraj429/firefly-3-sms-auto-scanner.git
cd firefly-3-sms-auto-scanner

# 2. Open in Android Studio (Hedgehog or newer)
# 3. Build & run on your device (USB debugging or wireless ADB)
```

#### Option B — Release APK *(coming soon)*
> A signed release APK will be published on the GitHub Releases page once the project reaches v1.0.

### First Run

1. **Grant permissions** when prompted:
   - `READ_SMS` — to scan your inbox
   - `RECEIVE_SMS` — for real-time detection
   - `POST_NOTIFICATIONS` — to show transaction alerts (Android 13+)

2. **Go to Setup tab → Enter your Firefly III details:**
   - Base URL (e.g. `https://firefly.yourdomain.com`)
   - Personal Access Token
   - Default Account ID (find it in Firefly → Accounts → click your account → note the ID in the URL)

3. **Tap "🔌 Test Connection"** — you should see a green ✅ response

4. **On the Transactions tab → tap "Sync"** to pull categories, budgets, tags, and accounts

5. **Go to SMS tab → select a date range → tap "📥 Scan SMS"** to load and parse messages

---

## 🏗️ Architecture

```
app/
├── model/              # Data classes (ParsedTransaction, FireflyModels, SmsMessage)
├── network/            # Retrofit API interface + client builder
├── parser/             # Regex-based SMS parser (Indian banking formats)
├── sms/                # SmsReader — ContentResolver queries with date filtering
├── notification/       # SmsReceiver (BroadcastReceiver) + NotificationHelper
├── prefs/              # SharedPreferences wrapper (AppPrefs)
├── viewmodel/          # SmsViewModel, TransactionViewModel, SetupViewModel,
│                       #   FireflyDataViewModel
├── ui/                 # Compose screens (Setup, SmsList, Transaction, Debug)
└── debug/              # DebugLog — in-memory timestamped log
```

**Tech stack:**
- **Kotlin** + **Coroutines** for async work
- **Jetpack Compose** + **Material3** for UI
- **Retrofit 2** + **OkHttp 4** for Firefly III API calls
- **Navigation Compose** for screen routing
- **ViewModel + SharedPreferences** for state & persistence
- **BroadcastReceiver** for live SMS interception

---

## 🔧 Configuration Reference

| Field | Where to find it |
|---|---|
| **Base URL** | Your Firefly III instance URL (no trailing slash) |
| **Access Token** | Firefly III → Profile → OAuth → Personal Access Tokens → Create |
| **Account ID** | Firefly III → Accounts → click your main account → ID is in the URL |

---

## 🤝 Contributing

Contributions are what make open source great. Whether it's fixing a bug, adding a new bank SMS format, or improving the UI — **all PRs are welcome**.

👉 Please read **[CONTRIBUTING.md](CONTRIBUTING.md)** before opening a pull request.

### Quick ways to help

- 🐛 **[Report a bug](https://github.com/swaraj429/firefly-3-sms-auto-scanner/issues/new?template=bug_report.md)**
- 💡 **[Request a feature](https://github.com/swaraj429/firefly-3-sms-auto-scanner/issues/new?template=feature_request.md)**
- 🏦 **Add support for a new bank SMS format** — see `SmsParser.kt`
- 🌍 **Add localisation** for non-Indian banking formats
- 📝 **Improve documentation**

---

## 🗺️ Roadmap

- [ ] **v1.0** — Stable release + signed APK
- [ ] Auto-send mode (skip review, send all transactions instantly)
- [ ] Rule-based auto-categorisation (e.g. "SWIGGY" → Food & Dining)
- [ ] Support for multiple Firefly III accounts
- [ ] Widget showing today's spend
- [ ] Import from CSV/bank statement
- [ ] Support non-Indian SMS formats (EU, US bank patterns)

---

## ⚠️ Privacy & Security

- **No data leaves your device** except to your own Firefly III instance
- SMS content is processed entirely on-device
- No analytics, no crash reporting, no third-party SDKs (other than OkHttp/Retrofit)
- Your access token is stored in **SharedPreferences** — consider using Android Keystore for production hardening (contributions welcome!)

---

## 📄 License

```
MIT License

Copyright (c) 2025 Swaraj

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

## 🙏 Acknowledgements

- [Firefly III](https://www.firefly-iii.org/) — the excellent self-hosted finance manager this app is built around
- [James Cole](https://github.com/JC5) — Firefly III creator
- Indian banking community for SMS format research

---

<div align="center">

**If this project helps you, please ⭐ star it on GitHub — it really helps!**

Made with ❤️ for the self-hosted finance community

</div>
