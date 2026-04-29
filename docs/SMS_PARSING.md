# 🔍 SMS Parsing Engine

The parser is the heart of the app. This document explains every decision made in `SmsParser.kt`, how to extend it, and how to test your changes.

---

## Overview

`SmsParser` is a Kotlin `object` (singleton). It takes a raw `SmsMessage` and returns a `ParsedTransaction` or `null` (if the SMS doesn't look like a financial transaction).

```
SmsMessage (sender, body, timestamp)
         │
         ▼
  extractAmount(body)
  [3 regex patterns tried in order]
         │
         ├── No match or amount ≤ 0 ──────────► return null
         │                                       (message silently skipped)
         │
         └── Amount found
                  │
                  ▼
         determineType(body)
         [Keyword scanning]
                  │
                  ▼
         ParsedTransaction
         (amount, type, rawMessage, sender, timestamp)
```

This deliberately simple approach parses approximately 60-70% of Indian banking SMS messages correctly out of the box. It is designed to be extended, not to be a perfect solution.

---

## Step 1: Amount Extraction

Three regex patterns are tried **in order**. The first match wins.

### Pattern 1 — Currency prefix

```
(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)
```

Matches: `Rs.2,500.00`, `Rs 1299`, `INR 15000`, `₹500.50`, `Rs.1,00,000`

**How it works:**
- `(?:Rs\.?|INR|₹)` — non-capturing group: matches `Rs`, `Rs.`, `INR`, or `₹`
- `\s*` — allows optional whitespace between symbol and number
- `([\d,]+\.?\d*)` — capture group: digits with optional commas and decimal point

### Pattern 2 — Currency suffix

```
([\d,]+\.?\d*)\s*(?:Rs\.?|INR|₹)
```

Matches: `2500Rs`, `1299 INR` (uncommon but some banks use this)

### Pattern 3 — Keyword prefix

```
(?:amount|amt)\s*(?:of\s*)?(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)
```

Matches: `Amount: 1,500.00`, `Amt of Rs. 250`, `amount of INR 5000`

### Amount normalisation

After the regex captures the raw string (e.g. `"2,500.00"`):
1. All commas are removed: `"2500.00"`
2. Parsed as `Double`
3. Rejected if `amount <= 0`

```
"2,500.00"  →  remove commas  →  "2500.00"  →  toDouble()  →  2500.0
                                                                  │
                                               ┌──────────────────┴──────────────────┐
                                               │ > 0                                  │ ≤ 0 or parse error
                                               ▼                                      ▼
                                         ✅ Use amount                       ⏭️ Try next pattern
```

---

## Step 2: Transaction Type Detection

The full SMS body is lowercased and scanned for keyword lists.

### Debit keywords (withdrawal)
```kotlin
"debited", "deducted", "withdrawn", "sent", "paid",
"purchase", "spent", "debit", "transferred", "txn of",
"payment of", "charged"
```

### Credit keywords (deposit)
```kotlin
"credited", "received", "deposited", "refund", "cashback",
"credit", "reversed", "added"
```

### Decision tree

```
Scan lowercased SMS body for keyword lists
│
├─ Has DEBIT keyword?
│   ├─ YES, no CREDIT  ──────────────────────────────────────► DEBIT
│   │
│   └─ YES + CREDIT → position tiebreak (first keyword wins)
│           ├─ debit keyword index < credit keyword index ───► DEBIT
│           └─ credit keyword index < debit keyword index ───► CREDIT
│
└─ No DEBIT keyword
    ├─ Has CREDIT keyword? ─── YES ─────────────────────────► CREDIT
    └─ Has CREDIT keyword? ─── NO  ─────────────────────────► UNKNOWN
```

**Why position-based tiebreaking?**

Some SMS messages contain both keywords. For example:
```
"₹5,000 debited from your account. If you haven't authorised this,
call us. Refund will be credited within 5 days."
```
Here `debited` appears before `credited`, so the type is correctly `DEBIT`.

---

## The `UNKNOWN` Type

When neither debit nor credit keywords are found, `type = TransactionType.UNKNOWN`. This is:
- Displayed as `⚪` in the UI
- Submitted to Firefly as `withdrawal` by default (see `toFireflyType()`)
- Correctable by the user via the type toggle chips in `TransactionScreen`

---

## SMS Format: Sample Coverage

The app ships with 10 sample messages covering major Indian banks:

| Sender | Bank | Type | Format |
|---|---|---|---|
| `VD-HDFCBK` | HDFC | Debit | POS/debit card |
| `AD-SBIINB` | SBI | Credit | NEFT credit |
| `VM-ICICIB` | ICICI | Debit | Card spend (Swiggy) |
| `BZ-AXISBK` | Axis | Debit | UPI sent |
| `JD-KOTAKB` | Kotak | Debit | Purchase |
| `AD-PNBSMS` | PNB | Credit | Deposit |
| `AX-BOIIND` | BOI | Credit | NEFT received |
| `VM-UNIONB` | Union Bank | Debit | ATM withdrawal |
| `DZ-CANBNK` | Canara Bank | Credit | Refund |
| `HP-IDBIBK` | IDBI | Debit | Transfer |

---

## Bank Sender Pattern Recognition

The parser also maintains a list of sender patterns to identify likely banking sources:

```kotlin
Regex("""^[A-Z]{2}-[A-Z]+""")   // e.g. VD-HDFCBK, AD-SBIINB (TRAI sender ID format)
Regex("""^[A-Z]{6,}""")          // e.g. HDFCBK, SBIINB (older format)
```

> **Note:** This list is currently unused in filtering — all messages are attempted regardless of sender. The pattern list is reserved for a future "bank sender filter" feature.

---

## Extending the Parser

### Adding a new amount format

Find a real SMS that doesn't parse correctly. For example:

```
"Your account XX1234 has been debited by Amount 1,500 on 29-Apr-25"
```

`Amount 1,500` matches no existing pattern (no `Rs`, `INR`, or `₹`). Add:

```kotlin
private val amountPatterns = listOf(
    // ... existing patterns ...
    Regex("""[Aa]mount\s+([0-9,]+\.?\d*)"""),  // "Amount 1,500"
)
```

### Adding a new type keyword

```
"Your Kotak Card XX1234 was swiped at SWIGGY for Rs.299. Available limit: Rs.45,000"
```

`swiped` is not in the debit keywords list. Add it:

```kotlin
private val debitKeywords = listOf(
    // ... existing ...
    "swiped",
)
```

### Adding sample messages for testing

Always add to `getSampleMessages()`:

```kotlin
SmsMessage(
    sender = "AD-KOTAKB",
    // ⚠️ Redact real account numbers — use XX1234 format
    body = "Your Kotak Card XX1234 was swiped at AMAZON for Rs.1,299 on 29-Apr-25. Avl Limit: Rs.45,000",
    timestamp = System.currentTimeMillis(),
    dateString = "29/04/2025 10:30"
),
```

---

## Known Limitations

| Limitation | Impact |
|---|---|
| Only processes the **first** amount found in the body | Rare: balance-included messages might match the wrong number |
| Keyword matching is greedy | "not debited" would still match "debited" |
| Indian formats only | No support for USD, EUR, GBP or non-Indian bank patterns |
| No OTP / marketing filter | Some OTPs containing amounts will be parsed as transactions |
| No duplicate detection | Same SMS scanned twice = two `ParsedTransaction` objects |

These are tracked as issues/roadmap items and are excellent first contribution targets.
