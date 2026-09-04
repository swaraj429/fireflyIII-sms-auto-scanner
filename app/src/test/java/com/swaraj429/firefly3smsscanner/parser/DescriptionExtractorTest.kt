package com.swaraj429.firefly3smsscanner.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionExtractorTest {

    @Test
    fun `test Axis Bank multiline spend extraction`() {
        val sms = """
            Axis Bank Alert:
            INR 1,234.00 spent
            on CARD no. XX9999
            at 28-02-26 14:15:30 IST
            ZEPTO
            Avl Limit: INR 50,000.00
        """.trimIndent()
        val desc = DescriptionExtractor.extractDescription(sms, "AXISBK", isExpense = true)
        assertEquals("Zepto", desc)
    }

    @Test
    fun `test ICICI Credit Card spend with Info field`() {
        val sms = "Dear Customer, your ICICI Bank Credit Card XX1002 has been used for a transaction of INR 3,999.00 on Feb 26, 2026 at 20:15:23. Info: IND*Microsoft. Available Credit Limit: INR 2,00,000.00."
        val desc = DescriptionExtractor.extractDescription(sms, "ICICIT", isExpense = true)
        assertEquals("Microsoft", desc)
    }

    @Test
    fun `test ICICI Bank UPI debit with Payee credited pattern`() {
        val sms = "Dear Customer, your Account ending 6818 has been debited for Rs 150.00 on 28-Feb-26; SHUBHAM LAXMAN credited. Call 18002662 for dispute."
        val desc = DescriptionExtractor.extractDescription(sms, "ICICIT", isExpense = true)
        assertEquals("Shubham Laxman", desc)
    }

    @Test
    fun `test ICICI Bank UPI credit with Payer name`() {
        val sms = "Dear Customer, your Account ending 6818 has been credited with Rs 500.00 on 28-Feb-26 by account linked to UPI id SNEHAL SUNIL KU (UPI Ref no 123456789)."
        val desc = DescriptionExtractor.extractDescription(sms, "ICICIT", isExpense = false)
        assertEquals("Snehal Sunil Ku", desc)
    }

    @Test
    fun `test SBI Card spend pattern`() {
        val sms = "Rs.850.00 spent on your SBI Card ending 4321 at ZOMATO on 27/02/26. Trxn not done by you? Call 18601801290."
        val desc = DescriptionExtractor.extractDescription(sms, "SBICRD", isExpense = true)
        assertEquals("Zomato", desc)
    }

    @Test
    fun `test Paytm paid to payee pattern`() {
        val sms = "Rs 350 paid to Swiggy from your Paytm Payments Bank A/c ending 1234 on 28-02-2026. Ref no: 123456."
        val desc = DescriptionExtractor.extractDescription(sms, "PAYTMB", isExpense = true)
        assertEquals("Swiggy", desc)
    }

    @Test
    fun `test FASTag toll plaza deduction`() {
        val sms = "Toll of Rs. 85.00 deducted from your FASTag linked to A/c ending 6818 at Khed Shivapur Toll Plaza on 25-Feb-26."
        val desc = DescriptionExtractor.extractDescription(sms, "BOBTXN", isExpense = true)
        assertEquals("Khed Shivapur Toll Plaza", desc)
    }

    @Test
    fun `test Simpl PayLater merchant purchase`() {
        val sms = "You made a purchase of Rs. 420.00 at Blinkit using Simpl. Your bill is due on 15th Mar."
        val desc = DescriptionExtractor.extractDescription(sms, "SMPLPL", isExpense = true)
        assertEquals("Blinkit", desc)
    }

    @Test
    fun `test Salary NEFT corporate payout`() {
        val sms = "Dear Customer, Account ending 6818 credited with INR 75,000.00 on 28-Feb-26 by NEFT (Ref no 12345678). Info NEFT-12345678-KPIT TECHNOLOGIES."
        val desc = DescriptionExtractor.extractDescription(sms, "BOBTXN", isExpense = false)
        assertEquals("Kpit Technologies", desc)
    }

    @Test
    fun `test Credit Card bill payment acknowledgment`() {
        val sms = "Thank you for payment of Rs 12,450.00 towards your SBI Card ending 4321 received on 26-Feb-26."
        val desc = DescriptionExtractor.extractDescription(sms, "SBICRD", isExpense = true)
        assertEquals("SBI Card Bill Payment", desc)
    }

    @Test
    fun `test SIP systematic debit`() {
        val sms = "Rs 5000.00 debited from A/c 6818 on 05-Feb-26 for SIP Investment UTI Nifty 50 Index Fund."
        val desc = DescriptionExtractor.extractDescription(sms, "BOBTXN", isExpense = true)
        assertEquals("SIP - UTI Nifty 50 Index Fund", desc)
    }

    @Test
    fun `test UPI VPA known merchant dictionary resolution`() {
        val sms = "Paid Rs. 249.00 to fkrt@ybl using UPI Ref 405928192839 on 22-Feb-26."
        val desc = DescriptionExtractor.extractDescription(sms, "BOBTXN", isExpense = true)
        assertEquals("Flipkart", desc)
    }

    @Test
    fun `test UPI VPA person name cleanup`() {
        val sms = "Paid Rs. 100.00 to ketanj895@okhdfcbank using UPI."
        val desc = DescriptionExtractor.extractDescription(sms, "BOBTXN", isExpense = true)
        assertEquals("Ketan J", desc)
    }

    @Test
    fun `test contextual fallback with bank and account`() {
        val sms = "Your a/c no. XX6818 is debited for Rs.200.00 on 10-Feb-26."
        val desc = DescriptionExtractor.extractDescription(sms, "AD-BOBTXN", isExpense = true)
        assertEquals("Bank of Baroda Debit (...6818)", desc)
    }

    @Test
    fun `test isRawSenderOrEmpty identifies raw senders and allows user custom descriptions`() {
        assertTrue(DescriptionExtractor.isRawSenderOrEmpty("", "AD-BOBTXN"))
        assertTrue(DescriptionExtractor.isRawSenderOrEmpty(null, "AD-BOBTXN"))
        assertTrue(DescriptionExtractor.isRawSenderOrEmpty("AD-BOBTXN", "AD-BOBTXN"))
        assertTrue(DescriptionExtractor.isRawSenderOrEmpty("BOBTXN", "AD-BOBTXN"))
        assertTrue(DescriptionExtractor.isRawSenderOrEmpty("ICICIT", "VM-ICICIT"))
        assertTrue(DescriptionExtractor.isRawSenderOrEmpty("AXISBK", "AXISBK"))

        // User custom description should NOT be identified as raw sender
        assertFalse(DescriptionExtractor.isRawSenderOrEmpty("Dinner with friends", "AD-BOBTXN"))
        assertFalse(DescriptionExtractor.isRawSenderOrEmpty("Flipkart", "BOBTXN"))
        assertFalse(DescriptionExtractor.isRawSenderOrEmpty("Amazon shopping", "VM-ICICIT"))
    }
}
