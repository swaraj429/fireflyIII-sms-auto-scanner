# Contributing to Firefly III SMS Scanner

First off — **thank you** for taking the time to contribute! 🎉

This project is community-driven and every contribution matters, whether it's a typo fix, a new bank SMS format, or a major feature.

---

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Commit Message Guidelines](#commit-message-guidelines)
- [Pull Request Process](#pull-request-process)
- [Adding Bank SMS Formats](#adding-bank-sms-formats)

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). By participating, you agree to uphold it. Please report unacceptable behaviour to the maintainer.

---

## How Can I Contribute?

### 🐛 Reporting Bugs

Before filing a bug report:
1. Search [existing issues](https://github.com/swaraj429/firefly-3-sms-auto-scanner/issues) to avoid duplicates
2. Check if the problem is reproducible on a clean install

When filing a bug, use the **[Bug Report template](.github/ISSUE_TEMPLATE/bug_report.md)** and include:
- The SMS message text that caused the issue (redact account numbers!)
- Your Android version and phone model
- The output from the **Debug** tab in the app

### 💡 Suggesting Features

Use the **[Feature Request template](.github/ISSUE_TEMPLATE/feature_request.md)**. Good feature requests include:
- A clear use case: *"As a user, I want to…"*
- Why it can't be solved with current functionality
- Optionally, a rough idea of how it could be implemented

### 🏦 Adding Bank SMS Patterns (Most Needed!)

This is the most impactful contribution you can make. Indian banking has dozens of SMS formats — see the dedicated section [below](#adding-bank-sms-formats).

### 🔨 Fixing Bugs / Implementing Features

Check the [issues labelled `good first issue`](https://github.com/swaraj429/firefly-3-sms-auto-scanner/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) if you're new to the codebase. Comment on an issue to let others know you're working on it.

---

## Development Setup

### Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Android SDK | API 26–34 |
| Kotlin | 1.9.22 |

### Steps

```bash
# 1. Fork the repo on GitHub, then clone your fork
git clone https://github.com/<YOUR_USERNAME>/firefly-3-sms-auto-scanner.git
cd firefly-3-sms-auto-scanner

# 2. Open in Android Studio
# File → Open → select the cloned directory

# 3. Let Gradle sync complete

# 4. Run on a physical device (SMS permissions don't work in emulator)
# or use sample data mode (no SMS permission needed)
```

> **Tip:** The app has a built-in **Debug tab** that logs every API call, SMS read, and parse result. Use it heavily during development.

---

## Project Structure

```
app/src/main/java/com/swaraj/fireflysmscanner/
│
├── model/                  # Pure data classes
│   ├── ParsedTransaction   # Core transaction model with Firefly metadata fields
│   ├── FireflyModels       # API request/response models
│   └── SmsMessage          # Raw SMS data from ContentResolver
│
├── parser/
│   └── SmsParser           # ← Most contributions go here (new bank patterns)
│
├── sms/
│   └── SmsReader           # ContentResolver queries; supports date range filtering
│
├── network/
│   ├── FireflyApi          # Retrofit interface (categories, tags, budgets, accounts)
│   └── RetrofitClient      # OkHttp + Retrofit builder with auth interceptor
│
├── notification/
│   ├── SmsReceiver         # BroadcastReceiver: SMS_RECEIVED + notification actions
│   └── NotificationHelper  # Channel creation, notification builder
│
├── prefs/
│   └── AppPrefs            # SharedPreferences wrapper
│
├── viewmodel/
│   ├── SetupViewModel      # Connection testing
│   ├── SmsViewModel        # SMS loading, parsing, date range state
│   ├── TransactionViewModel # Sending to Firefly III
│   └── FireflyDataViewModel # Fetching/caching categories, tags, budgets, accounts
│
├── ui/
│   ├── SetupScreen         # Configuration + live detection status
│   ├── SmsListScreen       # Date range picker + SMS list
│   ├── TransactionScreen   # Abacus-style editor cards
│   └── DebugScreen         # In-app log viewer
│
└── debug/
    └── DebugLog            # Thread-safe singleton log (main-thread routed)
```

---

## Coding Standards

### Kotlin Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `camelCase` for variables/functions, `PascalCase` for classes
- Prefer `val` over `var` where possible
- Avoid platform types — always specify nullability explicitly

### Compose UI
- One `@Composable` function per conceptual UI component
- Extract reusable composables into `private` functions within the same file
- Use `remember` and `rememberSaveable` appropriately
- **Never** mutate Compose state from a non-main thread — use `Handler(Looper.getMainLooper())` or `withContext(Dispatchers.Main)`

### Threading Rules
- All Firefly API calls must be in `viewModelScope.launch { }` or a coroutine
- `BroadcastReceiver.onReceive()` must use `goAsync()` for coroutine work
- Do not block the main thread

### No New Dependencies Without Discussion
If your PR adds a new library, explain why in the PR description. The project intentionally keeps its dependency footprint small.

---

## Commit Message Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

[optional body]

[optional footer]
```

**Types:**

| Type | When to use |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `parser` | Changes to SMS parsing patterns |
| `docs` | Documentation only |
| `style` | Formatting (no logic change) |
| `refactor` | Code restructure without feature/bug change |
| `test` | Adding or fixing tests |
| `chore` | Build system, dependencies |

**Examples:**
```
feat(notification): add "Send Later" snooze action

fix(parser): handle HDFC credit card SMS without "Rs." prefix

parser(axis): add support for Axis Bank UPI confirmation format

docs: add setup instructions for self-signed SSL certificates
```

---

## Pull Request Process

1. **Fork & branch** — create a branch from `main`:
   ```bash
   git checkout -b feat/my-feature
   # or
   git checkout -b fix/parser-hdfc-credit
   ```

2. **Make your changes** — keep PRs focused. One feature or fix per PR.

3. **Test on a real device** — SMS permissions and BroadcastReceiver behaviour can only be verified on physical hardware.

4. **Fill in the PR template** completely when opening the PR.

5. **Respond to review comments** — maintainers may request changes.

6. Once approved, your PR will be **squash-merged** into `main`.

### PR Checklist (also in the template)

- [ ] Tested on a physical Android device
- [ ] No new lint errors introduced
- [ ] New SMS patterns include at least 3 test message samples in comments
- [ ] Existing functionality not broken
- [ ] PR description explains *why*, not just *what*

---

## Adding Bank SMS Formats

The SMS parser lives in `app/src/main/java/com/swaraj/fireflysmscanner/parser/SmsParser.kt`.

### How the parser works

1. **Amount extraction** — tries a list of regex patterns matching `Rs.`, `INR`, `₹` prefixes/suffixes
2. **Type detection** — scans for debit/credit keyword lists; position of first match wins on conflicts
3. Returns `null` if no valid amount is found (message is silently skipped)

### To add a new pattern

**Step 1** — Add your regex to `amountPatterns` if the format isn't covered:
```kotlin
// Example: "Amount: 1,500.00" format
Regex("""Amount:\s*([0-9,]+\.?\d*)""", RegexOption.IGNORE_CASE),
```

**Step 2** — Add any missing debit/credit keywords:
```kotlin
private val debitKeywords = listOf(
    // ... existing ...
    "auto-debited", // add new keyword here
)
```

**Step 3** — Add sample messages to `getSampleMessages()` so others can test:
```kotlin
SmsMessage(
    sender = "AD-MYBANK",
    body = "Your account XX1234 debited with Amount: 1,500.00 on 29-Apr-25. Ref: 123456789",
    timestamp = System.currentTimeMillis(),
    dateString = "29/04/2025 10:00"
),
```

**Step 4** — Add a comment block above your samples noting the bank and format:
```kotlin
// ── MyBank debit confirmation (format introduced ~2023) ──────────────────
```

### Please include in your PR
- The **raw SMS text** (with account numbers replaced by `XXXX`)
- The **bank name** and approximate date when the format was introduced
- At least **3 different sample messages** covering edge cases (different amounts, date formats, etc.)

---

## Questions?

Open a [Discussion](https://github.com/swaraj429/firefly-3-sms-auto-scanner/discussions) — it's the best place for questions that aren't bugs or feature requests.

---

*Thank you again for contributing! Every improvement, no matter how small, makes this tool better for everyone managing their finances with Firefly III.* 🔥
