# 🔍 SMS Parsing Engine

The parser is the heart of the app. This document explains every decision made in `SmsParser.kt`, how to extend it, and how to test your changes.

---

## Overview

`SmsParser` is a Kotlin `object` (singleton). It takes a raw `SmsMessage` and returns a `ParsedTransaction` or `null` (if the SMS doesn't look like a financial transaction).

```
SmsMessage (sender, body, timestamp)
         │
         ▼
  isSpamOrNonTransactional(body)
  [Filters OTPs, promos, balance inquiries]
         │
         ├── True ────────────────────────────► return null (silently ignored)
         │
         └── False
                 │
                 ▼
          extractAmount(body)
          [Multi-pattern regex scan]
                 │
                 ├── No match or ≤ 0 ────────► return null
                 │
                 └── Valid amount
                         │
                         ▼
                  determineType(body)
                  [Debit vs Credit analysis]
                         │
                         ▼
                  DescriptionExtractor.extractDescription(...)
                  [15-tier payee & merchant extraction]
                         │
                         ▼
                  determinePaymentMode(body)
                  [UPI, Card, ATM, NetBanking detection]
                         │
                         ▼
                  ParsedTransaction
                  (amount, type, description, paymentMode, tags, ...)
```

This multi-stage pipeline reliably parses transactions across major Indian banks and payment systems, extracting actionable financial metadata while rejecting spam and OTPs.

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
- Correctable by the user via the type toggle chips in `TransactionEditorSheet`

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

## Step 3: Payee & Description Extraction (`DescriptionExtractor.kt`)

Extracted descriptions are resolved via `DescriptionExtractor.extractDescription(body, sender, isExpense)`. Instead of falling back to cryptic bank sender IDs like `BOBTXN` or `ICICIT`, it executes a 15-tier extraction hierarchy:

1. **Multiline Card Spends (e.g. Axis Bank)**: Extracts merchant from the line between timestamp and `Avl Limit` (e.g., `Zepto`, `Hare Krishn`).
2. **Card Info Field (e.g. ICICI Bank)**: Matches `Info: IND*<Merchant>`.
3. **Payee Credited (e.g. UPI Debits)**: Parses `<Payee> credited` from debits.
4. **Sender Credited (e.g. UPI Credits)**: Parses `from <Payer>` or `by account linked to UPI id <Payer>`.
5. **Card Spend at Merchant (e.g. SBI Card)**: Matches `spent on your ... Card ... at <Merchant>`.
6. **Wallet Payee (e.g. Paytm)**: Matches `paid to <Payee>`.
7. **FASTag Plazas**: Captures toll plaza name (`at <Plaza> Toll Plaza`).
8. **Simpl / PayLater**: Captures merchant name (`at <Merchant> using Simpl`).
9. **Salary / Corporate NEFT**: Extracts employer from `Info NEFT-...-<Employer>`.
10. **Credit Card Bill Payments**: Formats as `<Bank> Card Bill Payment`.
11. **Systematic Debits / SIP**: Formats as `SIP - <Fund Name>`.
12. **ATM Withdrawals**: Formats as `ATM Cash Withdrawal`.
13. **UPI VPA Resolution**: Normalizes known merchant dictionaries (`fkrt@ybl` → **Flipkart**, `swiggy@...` → **Swiggy**) and cleans personal UPI handles.
14. **General Preposition Matches**: Extracts payees following `to `, `at `, or preceding `credited`.
15. **Contextual Fallback**: Formats as `<Bank Name> Debit/Credit (...<Last 4 digits>)` using bank sender mapping and masked account numbers.

---

## Step 4: Payment Mode Detection

`determinePaymentMode(body)` automatically tags transactions:
- **UPI** — detects VPA, UPI references, `@upi`, `@okaxis`, etc.
- **Card** — credit / debit card spends
- **ATM** — cash withdrawals
- **NetBanking** — NEFT, IMPS, RTGS transfers
- **FASTag** — toll deductions

---

## Known Limitations & Progress

| Feature / Limitation | Status | Notes |
|---|---|---|
| First amount priority | Active | Balance-included messages might match the wrong number in rare cases |
| Keyword matching is greedy | Active | "not debited" could still match "debited" |
| Indian formats only | Active | Optimized for Indian banking (INR/₹) |
| OTP / marketing spam filter | ✅ **Resolved in Alpha 4** | `isSpamOrNonTransactional` filters out promotional texts, OTPs, and balance alerts |
| Duplicate detection | ✅ **Resolved in Alpha 4** | Room DB hash-based deduplication + Swipe-to-Dismiss for duplicate alerts |
| Payee / merchant extraction | ✅ **Resolved in Alpha 4** | 15-tier `DescriptionExtractor` resolves clean merchants and payees |

