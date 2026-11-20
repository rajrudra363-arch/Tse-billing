package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePdfPrinter
import com.example.util.QrGenerator

@Composable
fun TseShieldLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun sx(v: Float) = (v / 120f) * w
        fun sy(v: Float) = (v / 120f) * h

        val shieldPath = Path().apply {
            moveTo(sx(60f), sy(5f))
            lineTo(sx(108f), sy(23f))
            lineTo(sx(108f), sy(57f))
            cubicTo(sx(108f), sy(86f), sx(88f), sy(104f), sx(60f), sy(115f))
            cubicTo(sx(32f), sy(104f), sx(12f), sy(86f), sx(12f), sy(57f))
            lineTo(sx(12f), sy(23f))
            close()
        }

        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(listOf(Color(0xFF174F70), NavyDeep))
        )
        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(listOf(Color(0xFFFFF0A8), GoldAccent, Color(0xFFA96B12))),
            style = Stroke(width = w * 0.042f)
        )

        // Camera housing & lens
        drawCircle(
            color = Color(0xFFF5FBFF),
            radius = w * 0.16f,
            center = Offset(sx(60f), sy(52f))
        )
        drawCircle(
            color = TealAccent,
            radius = w * 0.16f,
            center = Offset(sx(60f), sy(52f)),
            style = Stroke(width = w * 0.025f)
        )
        drawCircle(
            color = Color(0xFF0D2C48),
            radius = w * 0.11f,
            center = Offset(sx(60f), sy(52f))
        )
        drawCircle(
            color = CyanGlow,
            radius = w * 0.055f,
            center = Offset(sx(60f), sy(52f))
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.02f,
            center = Offset(sx(56f), sy(48f))
        )

        // Gold accent divider bar
        drawLine(
            color = GoldAccent.copy(alpha = 0.85f),
            start = Offset(sx(24f), sy(82f)),
            end = Offset(sx(96f), sy(82f)),
            strokeWidth = h * 0.022f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun QrCodeImage(
    content: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallbackBase64: String = ""
) {
    val bitmap = remember(content, fallbackBase64) {
        if (content.isNotBlank()) QrGenerator.generateQrBitmap(content, 280)
        else if (fallbackBase64.isNotBlank()) InvoicePdfPrinter.decodeBase64Bitmap(fallbackBase64)
        else null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(3.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No QR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SignaturePadBox(
    hasSavedSignature: Boolean,
    onSignatureChanged: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize(600, 200)) }

    LaunchedEffect(hasSavedSignature) {
        if (!hasSavedSignature) {
            strokes.clear()
            activeStroke = emptyList()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Customer Signature (Draw with finger)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = {
                    strokes.clear()
                    activeStroke = emptyList()
                    onSignatureChanged(null)
                },
                modifier = Modifier.testTag("clear_signature_button")
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Text("Clear")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .onSizeChanged { padSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeStroke = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            activeStroke = activeStroke + change.position
                        },
                        onDragEnd = {
                            if (activeStroke.isNotEmpty()) {
                                strokes.add(activeStroke)
                                activeStroke = emptyList()
                                onSignatureChanged(renderStrokesToBitmap(strokes, padSize))
                            }
                        },
                        onDragCancel = {
                            activeStroke = emptyList()
                        }
                    )
                }
                .testTag("signature_pad_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Baseline guide
                drawLine(
                    color = Color(0xFFDDE5EB),
                    start = Offset(16f, size.height * 0.78f),
                    end = Offset(size.width - 16f, size.height * 0.78f),
                    strokeWidth = 2f
                )

                val allStrokes = if (activeStroke.isNotEmpty()) strokes + listOf(activeStroke) else strokes
                allStrokes.forEach { pts ->
                    if (pts.size > 1) {
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF111111),
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    } else if (pts.size == 1) {
                        drawCircle(color = Color(0xFF111111), radius = 2.5f, center = pts.first())
                    }
                }
            }
            if (strokes.isEmpty() && activeStroke.isEmpty()) {
                Text(
                    text = if (hasSavedSignature) "Saved signature attached (draw to replace)" else "Sign here...",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun renderStrokesToBitmap(strokes: List<List<Offset>>, size: IntSize): Bitmap? {
    if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = AndroidPaint.Style.STROKE
        strokeWidth = 5f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.forEach { pts ->
        if (pts.size > 1) {
            val path = AndroidPath().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            canvas.drawPath(path, paint)
        } else if (pts.size == 1) {
            canvas.drawCircle(pts.first().x, pts.first().y, 2.5f, paint)
        }
    }
    return bmp
}
