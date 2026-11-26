package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.EmployeeEntity
import com.example.data.EmployeeSalarySummary
import com.example.data.SalaryCalculationEngine
import com.example.data.SalaryPaymentEntity
import com.example.data.SalaryPaymentStatus
import com.example.data.UserSession
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BlueAction
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmployeeManagementScreen(
    user: UserSession,
    summaries: List<EmployeeSalarySummary>,
    salaryPayments: List<SalaryPaymentEntity>,
    onSaveEmployee: (EmployeeEntity, onSuccess: () -> Unit) -> Unit,
    onDeleteEmployee: (String) -> Unit,
    onRecordSalaryPayment: (
        employeeId: String,
        amountText: String,
        paymentDate: String,
        method: String,
        referenceNo: String,
        notes: String,
        cycleStart: String,
        cycleEnd: String,
        onSuccess: () -> Unit
    ) -> Unit
) {
    // Part 16: Strict RBAC guard — Super Admin ONLY
    if (!user.isSuperAdmin) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("employee_management_denied"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Access denied", tint = RedDue, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Super Admin Access Only",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = RedDueDark
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "EMPLOYEE MANAGEMENT & Payroll are strictly restricted to Super Admin accounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var reportDateFilter by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var editingEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var payTargetSummary by remember { mutableStateOf<EmployeeSalarySummary?>(null) }
    var deleteTargetEmp by remember { mutableStateOf<EmployeeEntity?>(null) }

    val filteredSummaries = remember(summaries, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        summaries.filter { s ->
            val matchesQuery = q.isEmpty() ||
                s.employee.name.lowercase(Locale.ROOT).contains(q) ||
                s.employee.employeeCode.lowercase(Locale.ROOT).contains(q) ||
                s.employee.mobile.contains(q)
            val matchesStatus = when (statusFilter) {
                "PENDING" -> s.status == SalaryPaymentStatus.PENDING
                "PARTIAL" -> s.status == SalaryPaymentStatus.PARTIALLY_PAID
                "PAID" -> s.status == SalaryPaymentStatus.FULLY_PAID
                else -> true
            }
            matchesQuery && matchesStatus
        }
    }

    val filteredDatePayments = remember(salaryPayments, reportDateFilter, searchQuery) {
        val dq = reportDateFilter.trim()
        val sq = searchQuery.trim().lowercase(Locale.ROOT)
        salaryPayments.filter { p ->
            (dq.isEmpty() || p.paymentDate.contains(dq)) &&
                (sq.isEmpty() || p.employeeId.lowercase(Locale.ROOT).contains(sq) || p.referenceNo.lowercase(Locale.ROOT).contains(sq))
        }.sortedByDescending { it.paymentDate }
    }

    val totalMonthlyPayroll = summaries.sumOf { it.earnedSalary }
    val totalPaidCycle = summaries.sumOf { it.totalPaidInCycle }
    val totalPendingCycle = summaries.sumOf { it.remainingBalance }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("employee_management_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = GoldAccent)
                            Text("EMPLOYEE MANAGEMENT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Button(
                            onClick = { showAddEmployeeDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                            modifier = Modifier.testTag("add_employee_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Staff", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Earned: ₹${String.format(Locale.US, "%.0f", totalMonthlyPayroll)}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Paid: ₹${String.format(Locale.US, "%.0f", totalPaidCycle)}", color = Color(0xFF86EFAC), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Pending: ₹${String.format(Locale.US, "%.0f", totalPendingCycle)}", color = GoldAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search & Status Filter
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Employee Name, Code or Mobile") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TealAccent) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("employee_search_input")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All (${summaries.size})", "PENDING" to "Pending", "PARTIAL" to "Partially Paid", "PAID" to "Fully Paid").forEach { (key, lbl) ->
                            FilterChip(
                                selected = statusFilter == key,
                                onClick = { statusFilter = key },
                                label = { Text(lbl, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("employee_filter_$key")
                            )
                        }
                    }
                }
            }
        }

        // Employee Cards
        if (filteredSummaries.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(44.dp), tint = TealAccent)
                        Text("No employees found", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(filteredSummaries, key = { it.employee.employeeId }) { summary ->
                val emp = summary.employee
                val statusColor = when (summary.status) {
                    SalaryPaymentStatus.FULLY_PAID -> GreenPaid
                    SalaryPaymentStatus.PARTIALLY_PAID -> AmberWarn
                    SalaryPaymentStatus.PENDING -> RedDueDark
                }
                val statusBg = when (summary.status) {
                    SalaryPaymentStatus.FULLY_PAID -> GreenPaidBg
                    SalaryPaymentStatus.PARTIALLY_PAID -> Color(0xFFFFFBEB)
                    SalaryPaymentStatus.PENDING -> RedDueBg
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("employee_card_${emp.employeeCode}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Badge, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                                    Text(emp.employeeCode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = TealAccent)
                                }
                                Text(emp.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${emp.mobile.ifBlank { "No mobile" }} · Monthly: ₹${String.format(Locale.US, "%.0f", emp.monthlySalary)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = summary.status.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "Hired: ${emp.hiringDate} · Start: ${emp.workStartDate}${if (emp.workStopDate.isNotBlank()) " · Stop: ${emp.workStopDate}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "Cycle: ${summary.cycleStart} to ${summary.cycleEnd} (${summary.eligibleDays}/${summary.calendarDaysInCycle} days)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Earned: ₹${String.format(Locale.US, "%.2f", summary.earnedSalary)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Paid: ₹${String.format(Locale.US, "%.2f", summary.totalPaidInCycle)}", style = MaterialTheme.typography.bodySmall, color = GreenPaid, fontWeight = FontWeight.Bold)
                                    Text("Due: ₹${String.format(Locale.US, "%.2f", summary.remainingBalance)}", style = MaterialTheme.typography.bodySmall, color = RedDueDark, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { payTargetSummary = summary },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                                modifier = Modifier.testTag("pay_salary_${emp.employeeCode}")
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pay Salary", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { editingEmployee = emp },
                                modifier = Modifier.testTag("edit_employee_${emp.employeeCode}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Employee", tint = TealAccent)
                            }
                            IconButton(
                                onClick = { deleteTargetEmp = emp },
                                modifier = Modifier.testTag("delete_employee_${emp.employeeCode}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Employee", tint = RedDue)
                            }
                        }
                    }
                }
            }
        }

        // Date-Wise & Employee-Wise Salary Payment Report
        item {
            val empById = remember(summaries) { summaries.associate { it.employee.employeeId to it.employee } }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date-Wise & Employee-Wise Salary Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = reportDateFilter,
                        onValueChange = { reportDateFilter = it },
                        label = { Text("Filter Date (YYYY-MM-DD or YYYY-MM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("salary_report_date_filter")
                    )
                    if (filteredDatePayments.isEmpty()) {
                        Text("No salary installments recorded matching filter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val totalFiltered = filteredDatePayments.sumOf { it.amount }
                        Text("Filtered Total Paid: ₹${String.format(Locale.US, "%.2f", totalFiltered)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = GreenPaid)
                        filteredDatePayments.take(30).forEach { pay ->
                            val empName = empById[pay.employeeId]?.name ?: pay.employeeId
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$empName · ${pay.paymentDate}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${pay.method}${if (pay.referenceNo.isNotBlank()) " · Ref: ${pay.referenceNo}" else ""}${if (pay.notes.isNotBlank()) " · ${pay.notes}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("₹${String.format(Locale.US, "%.2f", pay.amount)}", fontWeight = FontWeight.Black, color = GreenPaid)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showAddEmployeeDialog || editingEmployee != null) {
        AddOrEditEmployeeDialog(
            initial = editingEmployee,
            onDismiss = {
                showAddEmployeeDialog = false
                editingEmployee = null
            },
            onSave = { emp ->
                onSaveEmployee(emp) {
                    showAddEmployeeDialog = false
                    editingEmployee = null
                }
            }
        )
    }

    val payTarget = payTargetSummary
    if (payTarget != null) {
        RecordSalaryPaymentDialog(
            summary = payTarget,
            onDismiss = { payTargetSummary = null },
            onSave = { amt, date, method, refNo, notes ->
                onRecordSalaryPayment(
                    payTarget.employee.employeeId,
                    amt,
                    date,
                    method,
                    refNo,
                    notes,
                    payTarget.cycleStart,
                    payTarget.cycleEnd
                ) {
                    payTargetSummary = null
                }
            }
        )
    }

    val deleteEmp = deleteTargetEmp
    if (deleteEmp != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetEmp = null },
            title = { Text("Delete Employee (${deleteEmp.employeeCode})?") },
            text = { Text("Remove ${deleteEmp.name} from active Employees?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEmployee(deleteEmp.employeeId)
                        deleteTargetEmp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDue, contentColor = Color.White)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetEmp = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddOrEditEmployeeDialog(
    initial: EmployeeEntity?,
    onDismiss: () -> Unit,
    onSave: (EmployeeEntity) -> Unit
) {
    val today = remember { SalaryCalculationEngine.todayIso() }
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var mobile by remember(initial) { mutableStateOf(initial?.mobile.orEmpty()) }
    var monthlySalary by remember(initial) {
        mutableStateOf(
            if (initial == null || initial.monthlySalary <= 0.0) "" else initial.monthlySalary.toInt().toString()
        )
    }
    var hiringDate by remember(initial) { mutableStateOf(initial?.hiringDate?.ifBlank { today } ?: today) }
    var workStartDate by remember(initial) { mutableStateOf(initial?.workStartDate?.ifBlank { today } ?: today) }
    var workStopDate by remember(initial) { mutableStateOf(initial?.workStopDate.orEmpty()) }
    var address by remember(initial) { mutableStateOf(initial?.address.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Employee" else "Edit Employee (${initial.employeeCode})", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Employee Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emp_name_input"))
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emp_mobile_input"))
                OutlinedTextField(value = monthlySalary, onValueChange = { monthlySalary = it }, label = { Text("Monthly Salary (₹) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emp_salary_input"))
                OutlinedTextField(value = hiringDate, onValueChange = { hiringDate = it }, label = { Text("Hiring Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emp_hiring_date_input"))
                OutlinedTextField(value = workStartDate, onValueChange = { workStartDate = it }, label = { Text("Work Start Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emp_start_date_input"))
                OutlinedTextField(value = workStopDate, onValueChange = { workStopDate = it }, label = { Text("Work Stop Date (Optional YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("emp_stop_date_input"))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val emp = (initial ?: EmployeeEntity(employeeId = "", employeeCode = "", name = name.trim(), hiringDate = hiringDate.trim(), workStartDate = workStartDate.trim())).copy(
                        name = name.trim(),
                        mobile = mobile.trim(),
                        monthlySalary = monthlySalary.toDoubleOrNull() ?: 0.0,
                        hiringDate = hiringDate.trim().ifBlank { today },
                        workStartDate = workStartDate.trim().ifBlank { today },
                        workStopDate = workStopDate.trim(),
                        address = address.trim(),
                        notes = notes.trim()
                    )
                    onSave(emp)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                modifier = Modifier.testTag("save_employee_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RecordSalaryPaymentDialog(
    summary: EmployeeSalarySummary,
    onDismiss: () -> Unit,
    onSave: (amountText: String, paymentDate: String, method: String, referenceNo: String, notes: String) -> Unit
) {
    val today = remember { SalaryCalculationEngine.todayIso() }
    var amount by remember(summary) {
        mutableStateOf(
            if (summary.remainingBalance > 0.0) String.format(Locale.US, "%.0f", summary.remainingBalance) else ""
        )
    }
    var paymentDate by remember { mutableStateOf(today) }
    var method by remember { mutableStateOf("UPI / Bank") }
    var referenceNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Salary Payment", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${summary.employee.name} (${summary.employee.employeeCode})", fontWeight = FontWeight.Bold, color = NavyDeep)
                Text("Cycle Earned: ₹${String.format(Locale.US, "%.2f", summary.earnedSalary)} · Remaining: ₹${String.format(Locale.US, "%.2f", summary.remainingBalance)}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Installment Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("salary_pay_amount_input"))
                OutlinedTextField(value = paymentDate, onValueChange = { paymentDate = it }, label = { Text("Payment Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("salary_pay_date_input"))
                OutlinedTextField(value = method, onValueChange = { method = it }, label = { Text("Payment Method (Cash / UPI / Bank)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = referenceNo, onValueChange = { referenceNo = it }, label = { Text("Reference / Transaction ID (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("salary_pay_ref_input"))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(amount, paymentDate, method, referenceNo, notes) },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_salary_payment_button")
            ) {
                Text("Record Payment", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
