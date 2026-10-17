package com.example.ui.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MetallicDark
import com.example.ui.theme.MetallicMuted
import com.example.ui.theme.MetallicSilver
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("ClickableViewAccessibility")
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onScannedResult: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = (context as? LifecycleOwner) ?: LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    val isScanned = remember { AtomicBoolean(false) }
    var cameraExecutor: ExecutorService? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraExecutor?.shutdown()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {}
        }
    }

    // Gallery Picker for QR Code
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                    val source = RGBLuminanceSource(width, height, pixels)
                    val reader = MultiFormatReader().apply {
                        setHints(
                            mapOf(
                                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                DecodeHintType.TRY_HARDER to true,
                                DecodeHintType.CHARACTER_SET to "UTF-8"
                            )
                        )
                    }
                    val result = decodeLuminanceSource(source, reader)
                    if (!result.isNullOrBlank()) {
                        onScannedResult(result.trim())
                        onDismiss()
                    } else {
                        Toast.makeText(context, "QR-код на изображении не найден", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "QR-код не распознан: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Animated scan line effect
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline_offset"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "СКАНЕР QR-КОДА",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MetallicDark)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Camera Preview or Permission Viewfinder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF070B12))
                        .border(1.5.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        // Live CameraX View with QR Analyzer
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                val executor = Executors.newSingleThreadExecutor().also {
                                    cameraExecutor = it
                                }

                                previewView.setOnTouchListener { _, event ->
                                    if (event.action == MotionEvent.ACTION_UP) {
                                        val factory = previewView.meteringPointFactory
                                        val point = factory.createPoint(event.x, event.y)
                                        val action = FocusMeteringAction.Builder(point).build()
                                        cameraInstance?.cameraControl?.startFocusAndMetering(action)
                                    }
                                    true
                                }

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    // High-definition image analysis for dense VLESS / VMess QR codes
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(1280, 720))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    val reader = MultiFormatReader().apply {
                                        setHints(
                                            mapOf(
                                                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                                DecodeHintType.TRY_HARDER to true,
                                                DecodeHintType.CHARACTER_SET to "UTF-8"
                                            )
                                        )
                                    }

                                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                        if (!isScanned.get()) {
                                            val text = scanBarcodeFromImageProxy(imageProxy, reader)
                                            if (!text.isNullOrBlank() && isScanned.compareAndSet(false, true)) {
                                                previewView.post {
                                                    onScannedResult(text.trim())
                                                    onDismiss()
                                                }
                                            }
                                        }
                                        imageProxy.close()
                                    }

                                    try {
                                        cameraProvider.unbindAll()
                                        val camera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                        cameraInstance = camera
                                    } catch (_: Exception) {}
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Cyber Reticle Overlay
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .border(1.5.dp, CyberCyan.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                        )

                        // Animated scanning beam
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(2.dp)
                                .align(Alignment.TopCenter)
                                .padding(top = (280 * scanOffset).dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            CyberCyan,
                                            Color.White,
                                            CyberCyan,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Flashlight / Torch toggle button in preview
                        IconButton(
                            onClick = {
                                val nextState = !isTorchEnabled
                                cameraInstance?.cameraControl?.enableTorch(nextState)
                                isTorchEnabled = nextState
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkSurface.copy(alpha = 0.75f))
                        ) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flashlight",
                                tint = if (isTorchEnabled) CyberCyan else MetallicSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Permission request prompt
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Требуется доступ к камере",
                                color = MetallicSilver,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Для сканирования QR-кода ключа или подписки разрешите приложению использовать камеру",
                                color = MetallicMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001426)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Предоставить доступ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gallery picker button
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать фото QR-кода из галереи", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Manual input fallback section
                Text(
                    text = "ИЛИ ВСТАВЬТЕ ССЫЛКУ / КЛЮЧ",
                    color = MetallicDark,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = {
                        Text(
                            "vless://, vmess://, trojan://, ss:// или ссылка https://...",
                            color = MetallicDark,
                            fontSize = 11.sp
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cb?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    manualInput = clip.trim()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = CyberCyan)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = MetallicSilver,
                        unfocusedTextColor = MetallicSilver,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                if (manualInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onScannedResult(manualInput.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001426)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Применить ключ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun decodeLuminanceSource(source: LuminanceSource, reader: MultiFormatReader): String? {
    // 1. HybridBinarizer
    try {
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val res = reader.decodeWithState(bitmap)
        if (!res.text.isNullOrBlank()) return res.text
    } catch (_: Exception) {}

    // 2. GlobalHistogramBinarizer (superior for glare, screen reflections, uneven lighting)
    try {
        val bitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
        val res = reader.decodeWithState(bitmap)
        if (!res.text.isNullOrBlank()) return res.text
    } catch (_: Exception) {}

    // 3. Inverted luminance (for dark mode websites and white-on-black QR codes)
    try {
        val inverted = source.invert()
        val bitmap = BinaryBitmap(HybridBinarizer(inverted))
        val res = reader.decodeWithState(bitmap)
        if (!res.text.isNullOrBlank()) return res.text
    } catch (_: Exception) {}

    return null
}

private fun scanBarcodeFromImageProxy(image: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        // 1. First path: CameraX toBitmap with hardware matrix rotation
        val bitmap = try {
            image.toBitmap()
        } catch (_: Throwable) {
            null
        }

        if (bitmap != null) {
            val rotation = image.imageInfo.rotationDegrees
            val targetBitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val width = targetBitmap.width
            val height = targetBitmap.height
            val pixels = IntArray(width * height)
            targetBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val text = decodeLuminanceSource(source, reader)
            if (!text.isNullOrBlank()) return text
        }

        // 2. Fallback path: Manual Y-plane extraction with array-level rotation
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val rowStride = plane.rowStride
        val imgWidth = image.width
        val imgHeight = image.height
        val rotation = image.imageInfo.rotationDegrees

        val yBytes = ByteArray(rowStride * imgHeight)
        val remaining = buffer.remaining()
        buffer.get(yBytes, 0, minOf(remaining, yBytes.size))

        val source = when (rotation) {
            90 -> {
                val rotated = ByteArray(imgWidth * imgHeight)
                for (y in 0 until imgHeight) {
                    val yRow = y * rowStride
                    for (x in 0 until imgWidth) {
                        rotated[x * imgHeight + (imgHeight - 1 - y)] = yBytes[yRow + x]
                    }
                }
                PlanarYUVLuminanceSource(rotated, imgHeight, imgWidth, 0, 0, imgHeight, imgWidth, false)
            }
            180 -> {
                val rotated = ByteArray(imgWidth * imgHeight)
                for (y in 0 until imgHeight) {
                    val yRow = y * rowStride
                    val targetRow = (imgHeight - 1 - y) * imgWidth
                    for (x in 0 until imgWidth) {
                        rotated[targetRow + (imgWidth - 1 - x)] = yBytes[yRow + x]
                    }
                }
                PlanarYUVLuminanceSource(rotated, imgWidth, imgHeight, 0, 0, imgWidth, imgHeight, false)
            }
            270 -> {
                val rotated = ByteArray(imgWidth * imgHeight)
                for (y in 0 until imgHeight) {
                    val yRow = y * rowStride
                    for (x in 0 until imgWidth) {
                        rotated[(imgWidth - 1 - x) * imgHeight + y] = yBytes[yRow + x]
                    }
                }
                PlanarYUVLuminanceSource(rotated, imgHeight, imgWidth, 0, 0, imgHeight, imgWidth, false)
            }
            else -> {
                PlanarYUVLuminanceSource(yBytes, rowStride, imgHeight, 0, 0, imgWidth, imgHeight, false)
            }
        }

        decodeLuminanceSource(source, reader)
    } catch (_: Throwable) {
        null
    } finally {
        reader.reset()
    }
}
