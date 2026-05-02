package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.model.FireflyAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccountMatcherTest {

    private val matcher = AccountMatcher(
        config = AccountMatcherConfig(
            walletKeywords = mapOf("AMAZON PAY" to "Amazon Wallet", "PAYTM" to "Paytm Wallet"),
            bankKeywords = mapOf("HDFC" to "HDFC", "ICICI" to "ICICI", "SBI" to "SBI")
        )
    )

    private val dummyAccounts = listOf(
        FireflyAccount("1", "HDFC Savings", "asset", accountNumber = "100000001234"),
        FireflyAccount("2", "ICICI Credit Card", "asset", accountNumber = "4111222233334444", accountRole = "ccAsset"),
        FireflyAccount("3", "SBI Joint", "asset", accountNumber = "200000005678"),
        FireflyAccount("4", "Amazon Wallet", "asset"),
        FireflyAccount("5", "Paytm Wallet", "asset")
    )

    @Test
    fun `test exact suffix match with masked number`() {
        val sms = "Rs 500 debited from a/c **1234 on 01-Jan."
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNotNull(result)
        assertEquals("1", result?.account?.id)
        assertEquals(ConfidenceScore.HIGH, result?.confidence)
    }

    @Test
    fun `test exact suffix match with ending keyword`() {
        val sms = "Your account ending 5678 has been credited with Rs 1000."
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNotNull(result)
        assertEquals("3", result?.account?.id)
        assertEquals(ConfidenceScore.HIGH, result?.confidence)
    }

    @Test
    fun `test credit card suffix match`() {
        val sms = "Rs. 200 spent on your ICICI Bank Card XX4444."
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNotNull(result)
        assertEquals("2", result?.account?.id)
        assertEquals(ConfidenceScore.HIGH, result?.confidence)
    }

    @Test
    fun `test wallet keyword match`() {
        val sms = "Paid Rs 150 using AMAZON PAY for Swiggy."
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNotNull(result)
        assertEquals("4", result?.account?.id)
        assertEquals(ConfidenceScore.HIGH, result?.confidence)
    }

    @Test
    fun `test bank keyword fallback match`() {
        // No account number, but has HDFC
        val sms = "Thanks for banking with HDFC. Rs 100 debited for charges."
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNotNull(result)
        assertEquals("1", result?.account?.id)
        assertEquals(ConfidenceScore.MEDIUM, result?.confidence)
    }

    @Test
    fun `test multiple matches prioritizes higher confidence`() {
        // Here we have HDFC (bank keyword) and 5678 (SBI account suffix)
        // High confidence suffix match should beat Medium confidence bank match
        val sms = "Fund transfer of Rs 500 from a/c 5678 via HDFC UPI."
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNotNull(result)
        assertEquals("3", result?.account?.id) // Matches SBI Joint because of 5678 suffix
        assertEquals(ConfidenceScore.HIGH, result?.confidence)
    }

    @Test
    fun `test no match returns null`() {
        val sms = "No account details here."
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNull(result)
    }

    @Test
    fun `test partial account number match`() {
        val sms = "Transfer of Rs 100 from A/c 00012." // "00012" is part of "100000001234"
        val result = matcher.findBestMatch(sms, dummyAccounts)
        assertNotNull(result)
        assertEquals("1", result?.account?.id)
        assertEquals(ConfidenceScore.LOW, result?.confidence)
    }
}
