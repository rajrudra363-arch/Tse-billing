package com.example

import com.example.data.AuthHasher
import com.example.data.InvoiceItem
import com.example.ui.BillDraft
import com.example.util.RupeesWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun billDraft_calculatesTotalsAndDueCorrectly() {
        val draft = BillDraft(
            items = listOf(
                InvoiceItem(description = "2MP Dome Camera", qty = 4.0, rate = 1200.0),
                InvoiceItem(description = "4 Channel DVR", qty = 1.0, rate = 3500.0)
            ),
            discountText = "300",
            paidAmountText = "5000"
        )
        assertEquals(8300.0, draft.subtotal, 0.001)
        assertEquals(300.0, draft.discount, 0.001)
        assertEquals(8000.0, draft.grandTotal, 0.001)
        assertEquals(5000.0, draft.paidAmount, 0.001)
        assertEquals(3000.0, draft.dueAmount, 0.001)
    }

    @Test
    fun billDraft_overpaymentClampsDueToZero() {
        val draft = BillDraft(
            items = listOf(InvoiceItem(description = "Cable", qty = 1.0, rate = 1000.0)),
            discountText = "0",
            paidAmountText = "1500"
        )
        assertEquals(0.0, draft.dueAmount, 0.001)
    }

    @Test
    fun rupeesWords_formatsIndianCurrency() {
        assertEquals("Zero Only", RupeesWords.convert(0.0))
        assertEquals("One Lakh Twenty Five Thousand Five Hundred Only", RupeesWords.convert(125500.0))
        assertEquals("One Hundred and Fifty Paise Only", RupeesWords.convert(100.50))
    }

    @Test
    fun authHasher_producesDeterministicSha256() {
        val h1 = AuthHasher.sha256("secret123")
        val h2 = AuthHasher.sha256("secret123")
        val h3 = AuthHasher.sha256("secret124")
        assertEquals(64, h1.length)
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
    }
}
