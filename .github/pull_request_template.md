## 📋 Description

<!-- What does this PR do? Why is it needed? Be specific. -->

Fixes # <!-- issue number if applicable -->

---

## 🔄 Type of Change

<!-- Check all that apply -->

- [ ] 🐛 Bug fix (non-breaking change that fixes an issue)
- [ ] ✨ New feature (non-breaking change that adds functionality)
- [ ] 🏦 New bank SMS format / parser improvement
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to change)
- [ ] 📝 Documentation update
- [ ] 🎨 Style / formatting (no logic change)
- [ ] ♻️ Refactor (no feature change, no bug fix)
- [ ] ⚡ Performance improvement

---

## 🧪 How Has This Been Tested?

<!-- Describe how you tested your changes. -->

- [ ] Tested on a **physical device** (required for SMS/notification features)
- [ ] Tested with sample data mode
- [ ] Tested against a live Firefly III instance

**Device tested on:** <!-- e.g. Samsung Galaxy S23, Android 14 -->

**Firefly III version:** <!-- e.g. 6.1.13, or N/A -->

---

## 📸 Screenshots (if UI changes)

<!-- Add before/after screenshots for any UI changes -->

| Before | After |
|--------|-------|
| | |

---

## 🏦 SMS Samples (if parser changes)

<!-- If you changed SmsParser.kt, include at least 3 sample messages and their expected parse output. Redact all account numbers. -->

```
SMS 1: 
Expected: Amount=X, Type=DEBIT/CREDIT

SMS 2:
Expected: Amount=X, Type=DEBIT/CREDIT

SMS 3:
Expected: Amount=X, Type=DEBIT/CREDIT
```

---

## ✅ Checklist

- [ ] My code follows the project's [coding standards](CONTRIBUTING.md#coding-standards)
- [ ] I have performed a self-review of my code
- [ ] I have added comments for non-obvious logic
- [ ] No new lint errors or warnings introduced
- [ ] Existing functionality is not broken
- [ ] I have updated relevant documentation (README, CONTRIBUTING, code comments)
- [ ] My commits follow the [commit message guidelines](CONTRIBUTING.md#commit-message-guidelines)
- [ ] I have not introduced any new third-party dependencies without discussion

---

## 📝 Additional Notes

<!-- Anything else the reviewer should know? API caveats, edge cases, known limitations... -->
