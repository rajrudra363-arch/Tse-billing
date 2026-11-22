package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BillEntity
import com.example.data.CustomerEntity
import com.example.data.CustomerHelpEntity
import com.example.data.DocumentType
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.ui.TechnicianPerformanceSummary
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuperAdminDashboardScreen(
    bills: List<BillEntity>,
    customers: List<CustomerEntity>,
    helpRequests: List<CustomerHelpEntity>,
    products: List<ProductEntity>,
    technicianPerformance: List<TechnicianPerformanceSummary>
) {
    val taxBills = remember(bills) {
        bills.filter { DocumentType.fromString(it.documentType) == DocumentType.BILL }
    }
    val quotations = remember(bills) {
        bills.filter { DocumentType.fromString(it.documentType) == DocumentType.QUOTATION }
    }
    val totalRevenue = taxBills.sumOf { it.grandTotal }
    val onlineRevenue = taxBills
        .filter { PaymentMode.fromString(it.paymentMode) == PaymentMode.ONLINE }
        .sumOf { it.paidAmount }
    val cashRevenue = taxBills
        .filter { PaymentMode.fromString(it.paymentMode) == PaymentMode.CASH }
        .sumOf { it.paidAmount }
    val totalDue = taxBills.sumOf { it.dueAmount }
    val quotationPipeline = quotations.sumOf { it.grandTotal }
    val helpSolved = helpRequests.count { it.status == "SOLVED" || it.status == "RESOLVED" }
    val helpOpen = helpRequests.count { it.status == "OPEN" || it.status == "ACCEPTED" }
    val avgRating = remember(taxBills) {
        val rated = taxBills.filter { it.staffRating in 1..5 }
        if (rated.isEmpty()) 0.0 else rated.map { it.staffRating }.average()
    }
    val recentTrend = remember(taxBills) {
        taxBills.take(8).reversed()
    }
    val folderCounts = remember(products) {
        products.groupBy { it.folder.ifBlank { "General" } }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("super_admin_dashboard"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Read-Only Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(34.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Business Analytics & Market Intelligence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Read-Only Overview · Live Revenue, Technicians, Customers & Market Pipeline",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }

        // KPI Grid
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "Billed Revenue",
                    value = "₹${String.format(Locale.US, "%.0f", totalRevenue)}",
                    subtitle = "${taxBills.size} Invoices",
                    bg = Color(0xFFE1ECF4),
                    accent = NavyPrimary,
                    modifier = Modifier.fillMaxWidth(0.48f)
                )
                KpiCard(
                    title = "Customer Due",
                    value = "₹${String.format(Locale.US, "%.0f", totalDue)}",
                    subtitle = "${taxBills.count { it.dueAmount > 0.009 }} Due Bills",
                    bg = if (totalDue > 0.009) RedDueBg else GreenPaidBg,
                    accent = if (totalDue > 0.009) RedDueDark else GreenPaid,
                    modifier = Modifier.fillMaxWidth(0.48f)
                )
                KpiCard(
                    title = "Quotations Pipeline",
                    value = "₹${String.format(Locale.US, "%.0f", quotationPipeline)}",
                    subtitle = "${quotations.size} Active Quotes",
                    bg = Color(0xFFE0F2FE),
                    accent = TealAccent,
                    modifier = Modifier.fillMaxWidth(0.48f)
                )
                KpiCard(
                    title = "Service & Customers",
                    value = "${customers.size} Clients",
                    subtitle = "Help: $helpSolved Solved / $helpOpen Active",
                    bg = Color(0xFFFFF8E1),
                    accent = AmberWarn,
                    modifier = Modifier.fillMaxWidth(0.48f)
                )
            }
        }

        // Revenue Trend Bar Chart
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recent Invoice Revenue Chart", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (recentTrend.isEmpty()) {
                        Text(
                            text = "No invoice transactions recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val maxVal = recentTrend.maxOf { it.grandTotal }.coerceAtLeast(1.0)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(vertical = 8.dp)
                                .testTag("analytics_revenue_bar_chart")
                        ) {
                            val count = recentTrend.size
                            val slotW = size.width / count
                            val barW = (slotW * 0.56f).coerceAtMost(48f)
                            recentTrend.forEachIndexed { idx, bill ->
                                val ratio = (bill.grandTotal / maxVal).toFloat().coerceIn(0.05f, 1f)
                                val barH = size.height * ratio
                                val x = idx * slotW + (slotW - barW) / 2f
                                val y = size.height - barH
                                drawRect(
                                    color = if (bill.dueAmount > 0.009) RedDue else TealAccent,
                                    topLeft = Offset(x, y),
                                    size = Size(barW, barH)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            recentTrend.forEach { bill ->
                                Text(
                                    text = bill.invoiceNo.takeLast(5),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Teal = Settled Paid · Red = Has Outstanding Due Balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Payment Split Donut Chart (Online vs Cash)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Online vs Cash Collection Split", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    val totalCollected = (onlineRevenue + cashRevenue).coerceAtLeast(0.01)
                    val onlineSweep = ((onlineRevenue / totalCollected) * 360.0).toFloat()
                    val cashSweep = (360f - onlineSweep).coerceAtLeast(0f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(120.dp)
                                .testTag("analytics_payment_donut_chart")
                        ) {
                            val strokeW = 24.dp.toPx()
                            if (onlineRevenue + cashRevenue <= 0.009) {
                                drawArc(
                                    color = Color(0xFFCBD5E1),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                                )
                            } else {
                                drawArc(
                                    color = TealAccent,
                                    startAngle = -90f,
                                    sweepAngle = onlineSweep,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                                )
                                drawArc(
                                    color = GreenPaid,
                                    startAngle = -90f + onlineSweep,
                                    sweepAngle = cashSweep,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Online UPI: ₹${String.format(Locale.US, "%.2f", onlineRevenue)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TealAccent
                            )
                            Text(
                                text = "Cash Collected: ₹${String.format(Locale.US, "%.2f", cashRevenue)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPaid
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (avgRating > 0.0) "Avg Service Rating: ${String.format(Locale.US, "%.1f", avgRating)}/5" else "No service ratings yet",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Technician Comparison Table
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Technician Output & Admin Cash Balance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (technicianPerformance.isEmpty()) {
                        Text("No technician activity recorded yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val maxTechTotal = technicianPerformance.maxOf { it.totalBilledAmount }.coerceAtLeast(1.0)
                        technicianPerformance.forEach { t ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${t.displayName} (@${t.username})",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.0f", t.totalBilledAmount)} · ${t.billCount} bills",
                                        fontWeight = FontWeight.SemiBold,
                                        color = TealAccent
                                    )
                                }
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                ) {
                                    drawRect(color = Color(0xFF334155).copy(alpha = 0.25f), size = size)
                                    val w = (t.totalBilledAmount / maxTechTotal).toFloat().coerceIn(0f, 1f) * size.width
                                    drawRect(color = CyanGlow, size = Size(w, size.height))
                                }
                                Text(
                                    text = "Online ₹${String.format(Locale.US, "%.0f", t.onlineTotal)} · Cash ₹${String.format(Locale.US, "%.0f", t.cashTotal)} · Admin Due ₹${String.format(Locale.US, "%.0f", t.adminDueBalance)} · Help ${t.helpSolvedCount}/${t.helpAcceptedCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // Product Catalog Folder Distribution
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Market Product Folder Breakdown", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    folderCounts.forEach { (folder, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(folder, fontWeight = FontWeight.SemiBold)
                            Text("$count item(s)", color = TealAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    bg: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color(0xFF0F172A), fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
        }
    }
}
