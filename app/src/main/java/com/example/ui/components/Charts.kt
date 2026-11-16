package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.MonthlySummary
import com.example.domain.util.IndianCurrencyFormatter
import com.example.ui.theme.ChartColors
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.TealAccent

@Composable
fun IncomeVsExpenseBarChart(
    series: List<MonthlySummary>,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) {
        EmptyChartPlaceholder("No monthly cash flow history yet", modifier)
        return
    }
    val maxVal = series.maxOf { maxOf(it.totalIncome, it.totalExpenses) }.coerceAtLeast(1000.0)
    val incomeColor = EmeraldVibrant
    val expenseColor = CrimsonAlert.copy(alpha = 0.85f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = incomeColor, label = "Income")
            LegendDot(color = expenseColor, label = "Expenses")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val w = size.width
            val h = size.height
            val slotW = w / series.size.coerceAtLeast(1)
            val barW = (slotW * 0.28f).coerceAtMost(36f)

            // Horizontal reference lines
            for (i in 1..3) {
                val y = h * (i / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f
                )
            }

            series.forEachIndexed { index, item ->
                val centerX = slotW * index + slotW / 2f
                val incH = ((item.totalIncome / maxVal) * (h * 0.9f)).toFloat().coerceAtLeast(4f)
                val expH = ((item.totalExpenses / maxVal) * (h * 0.9f)).toFloat().coerceAtLeast(4f)

                drawRoundRect(
                    color = incomeColor,
                    topLeft = Offset(centerX - barW - 4f, h - incH),
                    size = Size(barW, incH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = expenseColor,
                    topLeft = Offset(centerX + 4f, h - expH),
                    size = Size(barW, expH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            series.forEach { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = item.monthLabel.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = IndianCurrencyFormatter.formatCompact(item.totalExpenses),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SpendingTrendLineChart(
    points: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        EmptyChartPlaceholder("Add expenses to see spending trend", modifier)
        return
    }
    val maxVal = points.maxOf { it.second }.coerceAtLeast(100.0)
    val lineColor = MaterialTheme.colorScheme.primary
    val fillTop = lineColor.copy(alpha = 0.28f)
    val fillBottom = lineColor.copy(alpha = 0.01f)
    val dotBg = MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val w = size.width
            val h = size.height
            val padX = 20f
            val usableW = (w - padX * 2).coerceAtLeast(1f)
            val coords = points.mapIndexed { idx, pair ->
                val x = if (points.size == 1) w / 2f else padX + (usableW * idx / (points.size - 1))
                val y = h - ((pair.second / maxVal).toFloat() * (h * 0.82f)) - 12f
                Offset(x, y)
            }

            if (coords.size >= 2) {
                val linePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }
                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(coords.last().x, h)
                    lineTo(coords.first().x, h)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(listOf(fillTop, fillBottom))
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }

            coords.forEach { pt ->
                drawCircle(color = dotBg, radius = 9f, center = pt)
                drawCircle(color = lineColor, radius = 5.5f, center = pt)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { (label, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = IndianCurrencyFormatter.formatCompact(value),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    categories: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) {
        EmptyChartPlaceholder("No categorized expenses for this period", modifier)
        return
    }
    val total = categories.sumOf { it.second }.coerceAtLeast(1.0)
    val topItems = categories.take(6)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(128.dp)) {
                var startAngle = -90f
                val strokeWidth = 26f
                topItems.forEachIndexed { idx, (_, amount) ->
                    val sweep = ((amount / total) * 360.0).toFloat()
                    drawArc(
                        color = ChartColors[idx % ChartColors.size],
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = IndianCurrencyFormatter.formatCompact(total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            topItems.forEachIndexed { idx, (category, amount) ->
                val pct = ((amount / total) * 100.0).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(ChartColors[idx % ChartColors.size])
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CashInflowOutflowBar(
    inflow: Double,
    outflow: Double,
    modifier: Modifier = Modifier
) {
    val total = (inflow + outflow).coerceAtLeast(1.0)
    val inflowFraction = (inflow / total).toFloat().coerceIn(0.05f, 0.95f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Cash Inflow", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    IndianCurrencyFormatter.format(inflow),
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldVibrant,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Cash Outflow", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    IndianCurrencyFormatter.format(outflow),
                    style = MaterialTheme.typography.titleMedium,
                    color = CrimsonAlert,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(inflowFraction)
                    .height(14.dp)
                    .background(EmeraldVibrant)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .weight(1f - inflowFraction)
                    .height(14.dp)
                    .background(CrimsonAlert.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
fun SavingsTrendChart(
    series: List<MonthlySummary>,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) {
        EmptyChartPlaceholder("Savings trend will appear once transactions are logged", modifier)
        return
    }
    val maxSav = series.maxOf { it.totalSavings }.coerceAtLeast(1000.0)
    val barColor = TealAccent
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        series.forEach { month ->
            val ratio = (month.totalSavings / maxSav).toFloat().coerceIn(0.04f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = month.monthLabel.take(3),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(38.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(listOf(EmeraldDeep, barColor, EmeraldBright))
                            )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = IndianCurrencyFormatter.formatCompact(month.totalSavings),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyChartPlaceholder(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
