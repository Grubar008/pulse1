package com.pulse.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulse.app.ui.theme.PulseColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun Dial(
    modifier: Modifier = Modifier,
    value: Double,
    maxValue: Double = 100.0,
    label: String,
    sublabel: String,
    color: Color = PulseColors.Teal,
    onTap: (() -> Unit)? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val stroke = 14.dp.toPx()
            val radius = (min(size.width, size.height) - stroke) / 2
            val center = Offset(size.width / 2, size.height / 2)
            val startAngle = 135f
            val sweepMax = 270f

            drawArc(
                color = PulseColors.SurfaceHigh,
                startAngle = startAngle,
                sweepAngle = sweepMax,
                useCenter = false,
 topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke)
            )
            val sweep = ((value / maxValue).coerceIn(0.0, 1.0) * sweepMax).toFloat()
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Text(
            text = if (maxValue == 21.0) "%.1f".format(value) else "%.0f".format(value),
            style = MaterialTheme.typography.headlineLarge,
            color = color,
            textAlign = TextAlign.Center
        )
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(text = sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LineChart(
    modifier: Modifier = Modifier,
    series: List<Pair<Long, Double>>,
    color: Color = PulseColors.Teal,
    minY: Double? = null,
    maxY: Double? = null
) {
    if (series.size < 2) {
        Text("Not enough data", style = MaterialTheme.typography.bodySmall, modifier = modifier)
        return
    }
    Canvas(modifier = modifier.height(160.dp).fillMaxWidth()) {
        val lo = minY ?: series.minOf { it.second }
        val hi = maxY ?: series.maxOf { it.second }
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val x0 = series.first().first.toDouble()
        val x1 = series.last().first.toDouble()
        val xSpan = (x1 - x0).takeIf { it > 1e-9 } ?: 1.0

        val path = Path()
        series.forEachIndexed { i, (x, y) ->
            val px = ((x - x0) / xSpan * size.width).toFloat()
            val py = size.height - (((y - lo) / span) * size.height).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = color, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
fun BarChart(
    modifier: Modifier = Modifier,
    values: List<Double>,
    colorFor: (Double) -> Color = { PulseColors.Teal }
) {
    if (values.isEmpty()) return
    Canvas(modifier = modifier.height(160.dp).fillMaxWidth()) {
        val max = values.max().takeIf { it > 0 } ?: 1.0
        val barW = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = (v / max * size.height).toFloat()
            drawRect(
                color = colorFor(v),
                topLeft = Offset(i * barW + 1, size.height - h),
                size = Size(barW - 2, h)
            )
        }
    }
}

@Composable
fun Hypnogram(modifier: Modifier = Modifier, stages: List<List<Any>>) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val count = stages.size
            if (count < 2) return@Canvas
            val t0 = (stages.first()[0] as Number).toLong()
            val t1 = (stages.last()[1] as Number).toLong()
            val span = (t1 - t0).coerceAtLeast(1)
            val levelOf = { idx: Int ->
                when ((stages[idx][2] as String).uppercase()) {
                    "AWAKE" -> 4f; "REM" -> 3f; "LIGHT" -> 2f; else -> 1f
                }
            }
            for (i in 0 until count) {
                val s = (stages[i][0] as Number).toLong()
                val e = (stages[i][1] as Number).toLong()
                val x0 = (s - t0).toFloat() / span * size.width
                val x1 = (e - t0).toFloat() / span * size.width
                val level = levelOf(i)
                val y = (level / 4f) * (size.height * 0.8f) + size.height * 0.05f
                val c = when (level) {
                    4f -> PulseColors.Red; 3f -> PulseColors.Purple
                    2f -> PulseColors.Teal; else -> PulseColors.Blue
                }
                drawRect(
                    color = c,
                    topLeft = Offset(x0, y),
                    size = Size((x1 - x0).coerceAtLeast(1f), 8f)
                )
            }
        }
    }
}

@Composable
fun RowBar(label: String, value: Double, maxValue: Double, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("%.0f".format(value), style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val frac = if (maxValue > 0) (value / maxValue).toFloat().coerceIn(0f, 1f) else 0f
            drawRect(color = Color(0x33FFFFFF), topLeft = Offset(0f, 0f), size = size)
            drawRect(color = color, topLeft = Offset(0f, 0f), size = Size(size.width * frac, size.height))
        }
    }
}
