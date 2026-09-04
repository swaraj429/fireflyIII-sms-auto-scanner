package com.swaraj429.firefly3smsscanner.parser

import java.util.Locale

/**
 * Intelligent description and payee extractor for Indian financial SMS.
 *
 * Extracts clean, human-readable merchant and beneficiary names from:
 * - Credit Card spends (ICICI, Axis, SBI, RBL, HDFC)
 * - Bank account debits with credited payees (ICICI, SBI, RBL, BOB)
 * - Bank account credits with payer names
 * - Corporate NEFT/IMPS Salary & employer names
 * - FASTag toll plaza transactions
 * - Simpl, Slice, and PayLater platforms
 * - Paytm wallet & P2P transfers
 * - UPI VPA identities with clean merchant dictionary resolution
 * - Systematic debits (SIP, Loan EMIs)
 * - POS and ATM operations
 * - Contextual fallback resolving bank names and account numbers
 */
object DescriptionExtractor {

    private val BANK_NAMES = mapOf(
        "BOB" to "Bank of Baroda",
        "BOBTXN" to "Bank of Baroda",
        "BOBSMS" to "Bank of Baroda",
        "ICICI" to "ICICI Bank",
        "ICICIT" to "ICICI Bank",
        "ICICIB" to "ICICI Bank",
        "AXIS" to "Axis Bank",
        "AXISBK" to "Axis Bank",
        "RBL" to "RBL Bank",
        "RBLBNK" to "RBL Bank",
        "SBI" to "State Bank of India",
        "SBICRD" to "SBI Card",
        "SBIUPI" to "SBI UPI",
        "HDFC" to "HDFC Bank",
        "HDFCBK" to "HDFC Bank",
        "KOTAK" to "Kotak Bank",
        "KOTAKB" to "Kotak Bank",
        "SCBANK" to "Standard Chartered",
        "IDFC" to "IDFC FIRST Bank",
        "IDFCFB" to "IDFC FIRST Bank",
        "PAYTM" to "Paytm Payments Bank",
        "PAYTMB" to "Paytm Payments Bank",
        "IPAYTM" to "Paytm Wallet",
        "SMPLPL" to "Simpl PayLater",
        "SLCEIT" to "Slice Card",
        "BAJAJF" to "Bajaj Finserv",
        "JIOPAY" to "Jio",
        "AIRTEL" to "Airtel",
        "AIRBIL" to "Airtel"
    )

    private val KNOWN_VPA_MERCHANTS = mapOf(
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "blinkit" to "Blinkit",
        "zepto" to "Zepto",
        "fkrt" to "Flipkart",
        "flipkart" to "Flipkart",
        "amazon" to "Amazon",
        "apl" to "Amazon Pay",
        "amzn" to "Amazon",
        "uber" to "Uber",
        "ola" to "Ola",
        "rapido" to "Rapido",
        "cred" to "CRED",
        "dream11" to "Dream11",
        "groww" to "Groww",
        "zerodha" to "Zerodha",
        "netflix" to "Netflix",
        "spotify" to "Spotify",
        "hotstar" to "Disney+ Hotstar",
        "jiomart" to "JioMart",
        "bigbasket" to "BigBasket",
        "dmart" to "DMart",
        "tatacliq" to "Tata CLiQ",
        "nykaa" to "Nykaa",
        "myntra" to "Myntra",
        "makemytrip" to "MakeMyTrip",
        "irctc" to "IRCTC",
        "bookmyshow" to "BookMyShow"
    )

    // Precompiled regexes
    private val axisMultilineRegex1 = Regex("""Spent\s*(?:INR|Rs\.?)\s*[\d,.]+\s*\n[^\n]+\n\d{2}-\d{2}-\d{2,4}\s+[\d:]+(?:\s*IST)?\s*\n([^\n]+)\nAvl""", RegexOption.IGNORE_CASE)
    private val axisMultilineRegex2 = Regex("""Spent\s*\n[^\n]+\n[^\n]+\n\d{2}-\d{2}-\d{2,4}\s+[\d:]+\s*\n([^\n]+)\nAvl""", RegexOption.IGNORE_CASE)

    private val ccPaymentRegex1 = Regex("""payment\s+(?:of\s+.*?\s+)?(?:has\s+been\s+)?received\s+towards\s+(?:your\s+)?([A-Za-z0-9 ]+?\s+Card(?:\s+[X\d]+)?)""", RegexOption.IGNORE_CASE)
    private val ccPaymentRegex2 = Regex("""received\s+(?:the\s+)?payment\s+via\s+.*?\s+your\s+available\s+Credit\s+Limit""", RegexOption.IGNORE_CASE)

    private val cardSpendRegex1 = Regex("""spent\s+(?:using|on)\s+.*?\s+(?:Card|card)\s+.*?\s+on\s+\d{1,2}-[A-Za-z]{3}-\d{2,4}\s+(?:on|at)\s+([A-Za-z0-9 .\-_*&@]+?)(?:\.\s*Avl|\.\s*If|\.${'$'}|${'$'})""", RegexOption.IGNORE_CASE)
    private val cardSpendRegex2 = Regex("""spent\s+on\s+.*?\s+(?:Card|card)\s+.*?\s+(?:at|on)\s+([A-Za-z0-9 .\-_*&@]+?)(?:\.\s*Avl|\.\s*If|\.${'$'}|${'$'})""", RegexOption.IGNORE_CASE)
    private val sbiCardSpendRegex = Regex("""spent\s+on\s+your\s+SBI\s+Credit\s+Card\s+ending\s+\d+\s+at\s+([A-Za-z0-9 .\-_*&@]+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val emiCardSpendRegex = Regex("""EMI\s+card\s+ending\s+\d+\s+at\s+([A-Za-z0-9 .\-_*&@]+?)\s+for\s+a\s+loan""", RegexOption.IGNORE_CASE)
    private val rblCardSpendRegex = Regex("""by\s+use\s+of\s+Card\s+.*?\s+at\s+([A-Za-z0-9 .\-_*&@]+?)(?:\s+\d{3}-\d{3}-\d{4}|\.\s*For|\.${'$'}|${'$'})""", RegexOption.IGNORE_CASE)

    private val debitedPayeeRegex = Regex("""debited\s+(?:for|with)\s+(?:Rs\.?|INR)\s*[\d,.]+\s+(?:on\s+[^;]+)?[;&]\s*([A-Za-z0-9 .\-_&@]+?)\s+credited""", RegexOption.IGNORE_CASE)
    private val creditedPayerRegex = Regex("""credited\s+with\s+(?:Rs\.?|INR)\s*[\d,.]+\s+(?:on\s+.*?\s+)?from\s+([A-Za-z0-9 .\-_&@]+?)(?:\.\s*UPI|\.\s*-|\.${'$'}|${'$'})""", RegexOption.IGNORE_CASE)
    private val neftInfoRegex = Regex("""Info\s+(?:NEFT|IMPS|RTGS|UPI)-[A-Za-z0-9]+-([A-Za-z0-9 .]+?)(?:\.|\s+Available|${'$'})""", RegexOption.IGNORE_CASE)
    private val infoBilRegex = Regex("""InfoBIL\*([A-Za-z0-9 .]+?)(?:\.|\s+Avl|${'$'})""", RegexOption.IGNORE_CASE)

    private val simplChargedRegex = Regex("""(?:Rs\.?|INR)\s*[\d,.]+\s+on\s+([A-Za-z0-9 .\-_&]+?)\s+charged\s+via\s+Simpl""", RegexOption.IGNORE_CASE)
    private val tollPaidRegex = Regex("""toll\s+paid\s+from\s+.*?at\s+([A-Za-z0-9 .\-_]+?)(?:\s+on\s+\d|\.${'$'}|${'$'})""", RegexOption.IGNORE_CASE)
    private val paytmPaidRegex1 = Regex("""Paid\s+(?:Rs\.?|INR)\s*[\d,.]+\s+to\s+([A-Za-z0-9 .\-_&]+?)(?:\s+at\s+[A-Za-z]{3}|\s+at\s+\d|\s*\.\s*Order)""", RegexOption.IGNORE_CASE)
    private val paytmTransferredRegex = Regex("""transferred\s+to\s+([A-Za-z0-9 .\-_&]+?)(?:\([0-9]+\))?\s+at\s+""", RegexOption.IGNORE_CASE)
    private val debitedTowardsRegex = Regex("""debited\s+towards\s+([A-Za-z0-9 .\-_&]+?)(?:\s+for\s+(?:Rs\.?|INR)|\.|\,|${'$'})""", RegexOption.IGNORE_CASE)
    private val vpaCreditedRegex = Regex("""(?:credited\s+to|sent\s+to)\s+(?:VPA\s+)?([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)

    private val transferToAccRegex = Regex("""credited\s+to\s+a/c\s*(?:no\.?)?\s*([X\d]+)""", RegexOption.IGNORE_CASE)
    private val transferFromAccRegex = Regex("""debited\s+from\s+a/c\s*(?:no\.?)?\s*([X\d]+)""", RegexOption.IGNORE_CASE)
    private val iciciTransferAccRegex = Regex("""(?:&|\.)\s*(?:Acct|A/c)\s*([X\d]+)\s+credited""", RegexOption.IGNORE_CASE)

    private val bobBranchRegex = Regex("""\bAT\s+([A-Z0-9 ,.\-_]{3,40}?)(?:\.\s*Stay|\.\s*Get|\.\s*TollFree|\.${'$'}|${'$'})""")
    private val upiLiteRegex = Regex("""carried\s+out\s+(\d+)\s+transactions\s+worth\s+(?:Rs\.?|INR)\s*[\d,.]+\s+using\s+your\s+UPI\s+Lite\s+Wallet""", RegexOption.IGNORE_CASE)
    private val telecomRechargeRegex = Regex("""Recharge\s+of\s+(?:Rs\.?|INR)\s*[\d,.]+\s+is\s+successful\s+for\s+(?:your\s+)?([A-Za-z0-9 ]+?\s+(?:number\s+)?\d+)""", RegexOption.IGNORE_CASE)
    private val accountNumRegex = Regex("""(?:a/c|acct|account|card)\s*(?:no\.?)?\s*[:\s]*([xX*.]*\d{3,6})""", RegexOption.IGNORE_CASE)

    /**
     * Extracts a sensible, human-readable description for an SMS transaction.
     */
    fun extractDescription(body: String, sender: String, isExpense: Boolean = true): String {
        val lower = body.lowercase()

        // 1. Credit Card Bill payment
        val ccMatch = ccPaymentRegex1.find(body)
        if (ccMatch != null && ccMatch.groupValues.size > 1) {
            return cleanDescription("${ccMatch.groupValues[1].trim()} Payment")
        }
        if (ccPaymentRegex2.containsMatchIn(body)) {
            val cardName = if (sender.contains("SBI", ignoreCase = true)) "SBI Card" else "Credit Card"
            return "$cardName Bill Payment"
        }
        if (lower.contains("repayment was a success") || lower.contains("received for simpl pay later")) {
            return "Simpl PayLater Repayment"
        }

        // 2. Axis Bank multiline spend
        val axisMatch = axisMultilineRegex1.find(body) ?: axisMultilineRegex2.find(body)
        if (axisMatch != null && axisMatch.groupValues.size > 1) {
            val merchant = axisMatch.groupValues[1].trim()
            if (merchant.isNotBlank() && !merchant.startsWith("Avl", ignoreCase = true)) {
                return cleanMerchantName(merchant)
            }
        }

        // 3. Card spends at/on merchant (ICICI, SBI, RBL, HDFC)
        val cardMatch = cardSpendRegex1.find(body) ?: cardSpendRegex2.find(body) ?: sbiCardSpendRegex.find(body) ?: rblCardSpendRegex.find(body)
        if (cardMatch != null && cardMatch.groupValues.size > 1) {
            val raw = cardMatch.groupValues[1].trim()
            if (raw.isNotBlank()) return cleanMerchantName(raw)
        }
        val emiCardMatch = emiCardSpendRegex.find(body)
        if (emiCardMatch != null && emiCardMatch.groupValues.size > 1) {
            return "${cleanMerchantName(emiCardMatch.groupValues[1])} (EMI Card)"
        }

        // 4. Account debit with payee (ICICI, SBI, RBL)
        val debitedMatch = debitedPayeeRegex.find(body)
        if (debitedMatch != null && debitedMatch.groupValues.size > 1) {
            val cand = debitedMatch.groupValues[1].trim()
            if (!cand.startsWith("acct", ignoreCase = true) && !cand.startsWith("a/c", ignoreCase = true)) {
                return cleanMerchantName(cand)
            }
        }

        // 5. Account credit from payer
        val creditedMatch = creditedPayerRegex.find(body)
        if (creditedMatch != null && creditedMatch.groupValues.size > 1) {
            return cleanMerchantName(creditedMatch.groupValues[1])
        }

        // 6. Corporate NEFT / IMPS Salary / Info
        val neftMatch = neftInfoRegex.find(body)
        if (neftMatch != null && neftMatch.groupValues.size > 1) {
            return cleanMerchantName(neftMatch.groupValues[1])
        }
        val infoBilMatch = infoBilRegex.find(body)
        if (infoBilMatch != null && infoBilMatch.groupValues.size > 1) {
            return cleanDescription(infoBilMatch.groupValues[1])
        }

        // 7. Simpl PayLater
        val simplMatch = simplChargedRegex.find(body)
        if (simplMatch != null && simplMatch.groupValues.size > 1) {
            return cleanMerchantName(simplMatch.groupValues[1])
        }

        // 8. FASTag Tolls
        val tollMatch = tollPaidRegex.find(body)
        if (tollMatch != null && tollMatch.groupValues.size > 1) {
            var plaza = tollMatch.groupValues[1].trim()
            if (!plaza.endsWith("toll", ignoreCase = true) && !plaza.endsWith("plaza", ignoreCase = true)) {
                plaza += " Toll Plaza"
            }
            return toTitleCase(plaza)
        }

        // 9. Paytm payments
        val paytmMatch = paytmPaidRegex1.find(body) ?: paytmTransferredRegex.find(body)
        if (paytmMatch != null && paytmMatch.groupValues.size > 1) {
            return cleanMerchantName(paytmMatch.groupValues[1])
        }

        // 10. debited towards
        val towardsMatch = debitedTowardsRegex.find(body)
        if (towardsMatch != null && towardsMatch.groupValues.size > 1) {
            return cleanMerchantName(towardsMatch.groupValues[1])
        }

        // 11. UPI VPA
        val vpaMatch = vpaCreditedRegex.find(body)
        if (vpaMatch != null && vpaMatch.groupValues.size > 1) {
            return cleanVpa(vpaMatch.groupValues[1].trim())
        }

        // 12. Inter-account transfer
        val iciciTransfer = iciciTransferAccRegex.find(body)
        if (iciciTransfer != null && iciciTransfer.groupValues.size > 1) {
            return "Transfer to A/c ${iciciTransfer.groupValues[1].trim()}"
        }
        val transferTo = transferToAccRegex.find(body)
        if (transferTo != null && transferTo.groupValues.size > 1) {
            return "Transfer to A/c ${transferTo.groupValues[1].trim()}"
        }
        val transferFrom = transferFromAccRegex.find(body)
        if (transferFrom != null && transferFrom.groupValues.size > 1) {
            return "Transfer from A/c ${transferFrom.groupValues[1].trim()}"
        }

        // 13. POS & ATM operations
        if (lower.contains("at atm") || lower.contains("withdrawn from atm")) {
            return "ATM Cash Withdrawal"
        }
        if (lower.contains("at adc")) {
            return "Cash Deposit (CDM/ADC)"
        }
        if (lower.contains("at pos")) {
            val posRegex = Regex("""at\s+pos\s+([A-Za-z0-9 .\-_&]+?)(?:\s+ref|\s+tid|\s+avl|\s+bal|\.|\,|${'$'})""", RegexOption.IGNORE_CASE)
            val posMatch = posRegex.find(body)
            if (posMatch != null && posMatch.groupValues[1].trim().length > 2) {
                return "POS: ${posMatch.groupValues[1].trim()}"
            }
            return "POS Purchase"
        }
        val branchMatch = bobBranchRegex.find(body)
        if (branchMatch != null && branchMatch.groupValues.size > 1) {
            val loc = branchMatch.groupValues[1].trim().trim(',')
            if (!loc.startsWith("pos", ignoreCase = true) && !loc.startsWith("atm", ignoreCase = true) && !loc.startsWith("adc", ignoreCase = true)) {
                return toTitleCase(loc)
            }
        }

        // 14. UPI Lite Wallet
        val liteMatch = upiLiteRegex.find(body)
        if (liteMatch != null && liteMatch.groupValues.size > 1) {
            return "UPI Lite Wallet (${liteMatch.groupValues[1]} txns)"
        }
        if (lower.contains("upi lite wallet")) {
            return "UPI Lite Wallet"
        }

        // 15. Reversals / Refunds
        if (lower.contains("reversal of transaction") || lower.contains("reversed back") || lower.contains("refund")) {
            return "Transaction Reversal / Refund"
        }

        // 16. Telecom recharges
        val telecomMatch = telecomRechargeRegex.find(body)
        if (telecomMatch != null && telecomMatch.groupValues.size > 1) {
            return "Recharge: ${telecomMatch.groupValues[1].trim()}"
        }
        if (sender.contains("AIRTEL", ignoreCase = true) || sender.contains("AIRBIL", ignoreCase = true)) {
            return "Airtel Bill / Recharge"
        }
        if (sender.contains("JIOPAY", ignoreCase = true) || sender.contains("JIO", ignoreCase = true)) {
            return "Jio Bill / Recharge"
        }

        // 17. Contextual Fallback (Bank Name + Account)
        val bankName = resolveBankName(sender)
        val accMatch = accountNumRegex.find(body)
        val accStr = accMatch?.groupValues?.getOrNull(1)?.trim() ?: ""

        val action = if (isExpense) "Debit" else "Deposit"
        return if (accStr.isNotBlank()) {
            "$bankName $action ($accStr)"
        } else {
            "$bankName $action"
        }
    }

    private fun cleanVpa(vpa: String): String {
        val lower = vpa.lowercase()
        for ((key, name) in KNOWN_VPA_MERCHANTS) {
            if (lower.contains(key)) return name
        }
        if (lower.contains("add-money@paytm") || lower.contains("addmoney@paytm")) {
            return "Paytm Wallet Top-up"
        }
        if (lower.contains("billdesk")) {
            val sub = vpa.substringBefore("@").substringAfter(".", "")
            if (sub.isNotBlank()) {
                return "${sub.replace("-", " ").capitalizeWords()} (BillDesk)"
            }
            return "BillDesk"
        }
        if (lower.contains("bharatpe")) return "BharatPe Merchant"
        if (lower.startsWith("q") && (lower.endsWith("@ybl") || lower.endsWith("@ibl") || lower.endsWith("@axl"))) {
            return "PhonePe Merchant"
        }

        // Extract username from username@handle
        val username = vpa.substringBefore("@")
        if (username.matches(Regex("""^\d{10}${'$'}"""))) {
            return "UPI $username"
        }
        val cleaned = username.replace(Regex("""[\d._-]+${'$'}"""), "")
        if (cleaned.length >= 3) {
            return toTitleCase(cleaned.replace(".", " ").replace("_", " "))
        }
        return vpa
    }

    fun cleanMerchantName(raw: String): String {
        var clean = raw.trim()
        // Strip common bank prefixes: "IND*", "WWW ", "M/s ", "Mr ", "Miss "
        clean = clean.replace(Regex("""^(IND\*|WWW\s+|M/s\s+|Mr\s+|Miss\s+|Dr\s+)""", RegexOption.IGNORE_CASE), "")
        // Strip common suffixes: " - ICICI Bank", " Avl", " If not", etc.
        clean = clean.replace(Regex("""(?:\s*-\s*[A-Za-z]+ Bank|\.\s*Avl.*|\.\s*If not.*)${'$'}""", RegexOption.IGNORE_CASE), "")
        clean = clean.trim()
        return cleanDescription(toTitleCase(clean))
    }

    private fun cleanDescription(desc: String): String {
        return desc.trim()
            .replace(Regex("""\s+"""), " ")
            .trimEnd('.', ',', '-', ';', ' ')
            .take(50)
    }

    private fun toTitleCase(str: String): String {
        if (str.isBlank()) return str
        val isAllUpper = str.all { !it.isLetter() || it.isUpperCase() }
        val isAllLower = str.all { !it.isLetter() || it.isLowerCase() }
        if (!isAllUpper && !isAllLower) return str

        return str.lowercase(Locale.ROOT).split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    fun resolveBankName(sender: String): String {
        val clean = sender.replace(Regex("""^[A-Z]{2}-"""), "")
            .replace(Regex("""-[A-Z]${'$'}"""), "")
            .uppercase()
        for ((k, v) in BANK_NAMES) {
            if (clean.contains(k)) return v
        }
        return if (clean.isNotBlank()) clean else "Bank"
    }

    /**
     * Checks whether a stored description is empty or an uninformative raw sender code
     * (e.g. "BOBTXN", "AD-BOBTXN", "ICICIT", "AXISBK") that should be re-enriched.
     */
    fun isRawSenderOrEmpty(description: String?, sender: String): Boolean {
        if (description.isNullOrBlank()) return true
        val cleanDesc = description.trim()
        val cleanSender = sender.trim()
        if (cleanDesc.equals(cleanSender, ignoreCase = true)) return true
        val senderCore = cleanSender.replace(Regex("""^[A-Za-z]{2}-"""), "").replace(Regex("""-[A-Za-z]${'$'}"""), "").uppercase()
        if (cleanDesc.equals(senderCore, ignoreCase = true)) return true
        if (BANK_NAMES.containsKey(cleanDesc.uppercase())) return true
        if (cleanDesc.matches(Regex("""^[A-Za-z]{2}-[A-Za-z0-9]+${'$'}"""))) return true
        if (cleanDesc.matches(Regex("""^[A-Z0-9]{3,8}${'$'}""")) && senderCore.contains(cleanDesc.uppercase())) return true
        return false
    }
}

