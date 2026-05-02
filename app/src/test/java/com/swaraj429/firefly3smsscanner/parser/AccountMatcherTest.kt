package com.swaraj429.firefly3smsscanner.parser

import com.swaraj429.firefly3smsscanner.model.AccountIndex
import com.swaraj429.firefly3smsscanner.model.FireflyAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountMatcherTest {

    private val mockAccounts = listOf(
        FireflyAccount("1", "HDFC Savings", "asset", "00001234"),
        FireflyAccount("2", "ICICI Credit Card", "liability", "XXXX5678"),
        FireflyAccount("3", "SBI Savings", "asset", "9876543210"),
        FireflyAccount("4", "Axis Bank", "asset", "55554321")
    )

    private val index = AccountMatcher.buildIndex(mockAccounts)

    @Test
    fun testHdfcMatch() {
        val sms = "Alert: INR 500 debited from a/c **1234 on 01-Jan at Amazon."
        val matches = AccountMatcher.matchAccounts(sms, index)
        assertEquals(1, matches.size)
        assertEquals("1", matches.first().accountId)
        assertEquals("HDFC Savings", matches.first().name)
    }

    @Test
    fun testIciciMatch() {
        val sms = "Your card ending 5678 is used for Rs.500 at SWIGGY."
        val matches = AccountMatcher.matchAccounts(sms, index)
        assertEquals(1, matches.size)
        assertEquals("2", matches.first().accountId)
        assertEquals("ICICI Credit Card", matches.first().name)
    }

    @Test
    fun testSbiMatch() {
        val sms = "A/c 3210 credited with Rs.15000 on 14JAN by NEFT."
        val matches = AccountMatcher.matchAccounts(sms, index)
        assertEquals(1, matches.size)
        assertEquals("3", matches.first().accountId)
        assertEquals("SBI Savings", matches.first().name)
    }

    @Test
    fun testAxisMatch() {
        val sms = "INR 500.00 sent via UPI from A/C XX4321 to paytm@upi"
        val matches = AccountMatcher.matchAccounts(sms, index)
        assertEquals(1, matches.size)
        assertEquals("4", matches.first().accountId)
    }

    @Test
    fun testMultipleMatches_PrefersCard() {
        // If we had two accounts ending in 1234
        val duplicateIndex = AccountMatcher.buildIndex(listOf(
            FireflyAccount("1", "HDFC Savings", "asset", "00001234"),
            FireflyAccount("2", "HDFC Credit Card", "liability", "XXXX1234")
        ))
        
        // SMS specifically mentions "card"
        val sms = "Alert: INR 500 spent on Card XX1234."
        val matches = AccountMatcher.matchAccounts(sms, duplicateIndex)
        assertEquals(1, matches.size)
        assertEquals("2", matches.first().accountId) // Resolves to credit card
    }

    @Test
    fun testMultipleMatches_PrefersAccount() {
        val duplicateIndex = AccountMatcher.buildIndex(listOf(
            FireflyAccount("1", "HDFC Savings", "asset", "00001234"),
            FireflyAccount("2", "HDFC Credit Card", "liability", "XXXX1234")
        ))
        
        // SMS specifically mentions "a/c"
        val sms = "Alert: INR 500 debited from a/c **1234."
        val matches = AccountMatcher.matchAccounts(sms, duplicateIndex)
        assertEquals(1, matches.size)
        assertEquals("1", matches.first().accountId) // Resolves to savings account
    }

    @Test
    fun testNoMatch() {
        val sms = "Alert: INR 500 debited from a/c **9999."
        val matches = AccountMatcher.matchAccounts(sms, index)
        assertEquals(0, matches.size)
    }
}
