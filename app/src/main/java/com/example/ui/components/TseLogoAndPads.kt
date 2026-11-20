package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePdfPrinter
import com.example.util.QrGenerator
import java.io.File

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
fun CameraSignatureBox(
    signatureBase64: String,
    onSignatureChanged: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val decodedBitmap = remember(signatureBase64) {
        InvoicePdfPrinter.decodeBase64Bitmap(signatureBase64)
    }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null && file.exists()) {
            val bmp = decodeScaledSignatureFile(file, maxDim = 800)
            file.delete()
            if (bmp != null) {
                onSignatureChanged(bmp)
            } else {
                Toast.makeText(context, "Could not read scanned signature photo", Toast.LENGTH_SHORT).show()
            }
        } else {
            file?.delete()
        }
    }

    fun startCameraFlow() {
        val dir = File(context.cacheDir, "signatures").apply { mkdirs() }
        val photoFile = File(dir, "cust_sig_${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, "Camera storage error", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCaptureFile = photoFile
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraFlow()
        } else {
            Toast.makeText(
                context,
                "Camera permission required to scan customer signature",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Customer Signature (Camera Scan)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (signatureBase64.isNotBlank()) {
                OutlinedButton(
                    onClick = { onSignatureChanged(null) },
                    modifier = Modifier.testTag("clear_signature_button")
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Text("Clear")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .testTag("signature_scan_preview")
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = "Scanned Customer Signature",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Scan customer's paper signature using Camera",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Button(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    startCameraFlow()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("scan_signature_camera_button")
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (decodedBitmap != null) "Re-scan Customer Signature (Camera)" else "Scan Customer Signature (Camera)",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun decodeScaledSignatureFile(file: File, maxDim: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val scale = minOf(
            maxDim.toFloat() / decoded.width.coerceAtLeast(1),
            maxDim.toFloat() / decoded.height.coerceAtLeast(1),
            1f
        )
        if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
    }.getOrNull()
}
