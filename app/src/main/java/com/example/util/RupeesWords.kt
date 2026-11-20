package com.example.util

import kotlin.math.floor
import kotlin.math.roundToInt

object RupeesWords {
    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    private fun twoDigits(n: Int): String {
        return if (n < 20) ones[n]
        else tens[n / 10] + if (n % 10 != 0) " ${ones[n % 10]}" else ""
    }

    private fun convertWhole(n: Long): String {
        if (n <= 0L) return ""
        val sb = StringBuilder()
        var rem = n
        if (rem >= 10_000_000L) {
            sb.append(convertWhole(rem / 10_000_000L)).append(" Crore ")
            rem %= 10_000_000L
        }
        if (rem >= 100_000L) {
            sb.append(twoDigits((rem / 100_000L).toInt())).append(" Lakh ")
            rem %= 100_000L
        }
        if (rem >= 1_000L) {
            sb.append(twoDigits((rem / 1_000L).toInt())).append(" Thousand ")
            rem %= 1_000L
        }
        if (rem >= 100L) {
            sb.append(ones[(rem / 100L).toInt()]).append(" Hundred ")
            rem %= 100L
        }
        if (rem > 0L) {
            sb.append(twoDigits(rem.toInt()))
        }
        return sb.toString().trim()
    }

    fun convert(amount: Double): String {
        val safe = amount.coerceAtLeast(0.0)
        val whole = floor(safe).toLong()
        val paise = ((safe - whole) * 100.0).roundToInt().coerceIn(0, 99)
        val wholeWords = convertWhole(whole).ifBlank { "Zero" }
        val paiseWords = if (paise > 0) " and ${twoDigits(paise)} Paise" else ""
        return "$wholeWords$paiseWords Only"
    }
}
