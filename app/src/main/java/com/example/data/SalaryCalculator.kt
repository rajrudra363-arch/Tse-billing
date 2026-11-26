package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

enum class SalaryPaymentStatus(val label: String) {
    PENDING("Pending"),
    PARTIALLY_PAID("Partially Paid"),
    FULLY_PAID("Fully Paid")
}

data class EmployeeSalarySummary(
    val employee: EmployeeEntity,
    val cycleStart: String,
    val cycleEnd: String,
    val calendarDaysInCycle: Int,
    val eligibleDays: Int,
    val dailySalary: Double,
    val earnedSalary: Double,
    val totalPaidInCycle: Double,
    val totalPaidAllTime: Double,
    val remainingBalance: Double,
    val status: SalaryPaymentStatus,
    val cyclePayments: List<SalaryPaymentEntity>
)

object SalaryCalculationEngine {
    private val utcZone: TimeZone = TimeZone.getTimeZone("UTC")

    fun todayIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Calendar.getInstance().time)
    }

    internal fun parseUtcCalendar(dateStr: String): Calendar? {
        val clean = dateStr.trim()
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(clean)) return null
        val parts = clean.split("-")
        val y = parts[0].toIntOrNull() ?: return null
        val m = (parts[1].toIntOrNull() ?: return null) - 1
        val d = parts[2].toIntOrNull() ?: return null
        return Calendar.getInstance(utcZone, Locale.US).apply {
            clear()
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            isLenient = false
        }.let { cal ->
            runCatching { cal.timeInMillis; cal }.getOrNull()
        }
    }

    internal fun formatUtcIso(cal: Calendar): String {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    internal fun daysBetweenInclusive(start: Calendar, end: Calendar): Int {
        val diffMs = end.timeInMillis - start.timeInMillis
        if (diffMs < 0L) return 0
        val days = (diffMs.toDouble() / 86_400_000.0).roundToLong().toInt()
        return (days + 1).coerceAtLeast(0)
    }

    internal fun daysBetweenExclusiveEnd(start: Calendar, endExclusive: Calendar): Int {
        val diffMs = endExclusive.timeInMillis - start.timeInMillis
        if (diffMs <= 0L) return 0
        return (diffMs.toDouble() / 86_400_000.0).roundToLong().toInt().coerceAtLeast(1)
    }

    private fun addOneMonthClamped(year: Int, monthZero: Int, anchorDay: Int): Calendar {
        var nextY = year
        var nextM = monthZero + 1
        if (nextM > Calendar.DECEMBER) {
            nextM = Calendar.JANUARY
            nextY += 1
        }
        val cal = Calendar.getInstance(utcZone, Locale.US).apply {
            clear()
            set(Calendar.YEAR, nextY)
            set(Calendar.MONTH, nextM)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceAtMost(maxDay))
        return cal
    }

    private fun makeClampedDate(year: Int, monthZero: Int, anchorDay: Int): Calendar {
        val cal = Calendar.getInstance(utcZone, Locale.US).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZero)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceAtMost(maxDay))
        return cal
    }

    /**
     * Resolves the active monthly salary cycle [cycleStart, cycleEndExclusive) anchored to
     * employee.workStartDate (or hiringDate fallback).
     */
    fun resolveSalaryCycle(
        workStartDate: String,
        hiringDate: String,
        referenceDate: String = todayIso()
    ): Pair<Calendar, Calendar> {
        val startCal = parseUtcCalendar(workStartDate)
            ?: parseUtcCalendar(hiringDate)
            ?: parseUtcCalendar(referenceDate)!!
        val refCal = parseUtcCalendar(referenceDate) ?: startCal
        val anchorDay = startCal.get(Calendar.DAY_OF_MONTH)

        if (refCal.timeInMillis <= startCal.timeInMillis) {
            val endEx = addOneMonthClamped(
                startCal.get(Calendar.YEAR),
                startCal.get(Calendar.MONTH),
                anchorDay
            )
            return startCal to endEx
        }

        val refYear = refCal.get(Calendar.YEAR)
        val refMonth = refCal.get(Calendar.MONTH)
        var cycleStart = makeClampedDate(refYear, refMonth, anchorDay)
        if (cycleStart.timeInMillis > refCal.timeInMillis) {
            var prevY = refYear
            var prevM = refMonth - 1
            if (prevM < Calendar.JANUARY) {
                prevM = Calendar.DECEMBER
                prevY -= 1
            }
            cycleStart = makeClampedDate(prevY, prevM, anchorDay)
        }
        if (cycleStart.timeInMillis < startCal.timeInMillis) {
            cycleStart = startCal
        }
        val cycleEndExclusive = addOneMonthClamped(
            cycleStart.get(Calendar.YEAR),
            cycleStart.get(Calendar.MONTH),
            anchorDay
        )
        return cycleStart to cycleEndExclusive
    }

    fun calculateEmployeeSummary(
        employee: EmployeeEntity,
        allPayments: List<SalaryPaymentEntity>,
        referenceDate: String = todayIso()
    ): EmployeeSalarySummary {
        val empPayments = allPayments.filter { it.employeeId == employee.employeeId }
        val (cycleStartCal, cycleEndExclusiveCal) = resolveSalaryCycle(
            workStartDate = employee.workStartDate,
            hiringDate = employee.hiringDate,
            referenceDate = referenceDate
        )
        val cycleStartIso = formatUtcIso(cycleStartCal)
        val cycleEndInclusiveCal = Calendar.getInstance(utcZone, Locale.US).apply {
            timeInMillis = cycleEndExclusiveCal.timeInMillis - 86_400_000L
        }
        val cycleEndIso = formatUtcIso(cycleEndInclusiveCal)

        val workStartCal = parseUtcCalendar(employee.workStartDate) ?: parseUtcCalendar(employee.hiringDate) ?: cycleStartCal
        val workStopCal = parseUtcCalendar(employee.workStopDate)
        val refCal = parseUtcCalendar(referenceDate) ?: cycleEndInclusiveCal

        val calendarDaysInCycle = daysBetweenExclusiveEnd(cycleStartCal, cycleEndExclusiveCal).coerceAtLeast(1)
        val monthly = employee.monthlySalary.coerceAtLeast(0.0)
        val dailySalary = if (monthly > 0.0) monthly / calendarDaysInCycle.toDouble() else 0.0

        val effectiveStart = if (workStartCal.timeInMillis > cycleStartCal.timeInMillis) workStartCal else cycleStartCal
        val stopBound = listOfNotNull(
            cycleEndInclusiveCal.timeInMillis,
            refCal.timeInMillis,
            workStopCal?.timeInMillis
        ).minOrNull() ?: cycleEndInclusiveCal.timeInMillis

        val effectiveEnd = Calendar.getInstance(utcZone, Locale.US).apply { timeInMillis = stopBound }
        val eligibleDays = if (effectiveEnd.timeInMillis >= effectiveStart.timeInMillis) {
            daysBetweenInclusive(effectiveStart, effectiveEnd).coerceAtMost(calendarDaysInCycle)
        } else {
            0
        }

        val earnedSalary = ((dailySalary * eligibleDays) * 100.0).roundToLong() / 100.0
        val cyclePayments = empPayments.filter { p ->
            (p.cycleStart.isBlank() && p.paymentDate >= cycleStartIso && p.paymentDate <= cycleEndIso) ||
                p.cycleStart == cycleStartIso
        }
        val totalPaidInCycle = cyclePayments.sumOf { it.amount.coerceAtLeast(0.0) }
        val totalPaidAllTime = empPayments.sumOf { it.amount.coerceAtLeast(0.0) }
        val remaining = ((earnedSalary - totalPaidInCycle).coerceAtLeast(0.0) * 100.0).roundToLong() / 100.0

        val status = when {
            earnedSalary <= 0.009 && totalPaidInCycle <= 0.009 -> SalaryPaymentStatus.PENDING
            totalPaidInCycle <= 0.009 -> SalaryPaymentStatus.PENDING
            remaining <= 0.009 -> SalaryPaymentStatus.FULLY_PAID
            else -> SalaryPaymentStatus.PARTIALLY_PAID
        }

        return EmployeeSalarySummary(
            employee = employee,
            cycleStart = cycleStartIso,
            cycleEnd = cycleEndIso,
            calendarDaysInCycle = calendarDaysInCycle,
            eligibleDays = eligibleDays,
            dailySalary = dailySalary,
            earnedSalary = earnedSalary,
            totalPaidInCycle = totalPaidInCycle,
            totalPaidAllTime = totalPaidAllTime,
            remainingBalance = remaining,
            status = status,
            cyclePayments = cyclePayments
        )
    }

    /**
     * Prevents accidental duplicate salary payments on the same date, same amount, and same reference/method.
     */
    fun isDuplicatePayment(
        existingPayments: List<SalaryPaymentEntity>,
        employeeId: String,
        paymentDate: String,
        amount: Double,
        referenceNo: String
    ): Boolean {
        val cleanRef = referenceNo.trim()
        return existingPayments.any { p ->
            p.employeeId == employeeId &&
                p.paymentDate.trim() == paymentDate.trim() &&
                kotlin.math.abs(p.amount - amount) < 0.01 &&
                (cleanRef.isNotBlank() && p.referenceNo.trim().equals(cleanRef, ignoreCase = true) ||
                    (cleanRef.isBlank() && p.referenceNo.isBlank()))
        }
    }
}
