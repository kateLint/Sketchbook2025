package com.keren.sketchbook2025

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    secondary = Color(0xFF03DAC6),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.Black,
                    onSecondary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White,
                )
            ) {
                SketchbookApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SketchbookApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx().toInt() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx().toInt() }

    val canvasBitmap = remember {
        ImageBitmap(screenWidth, screenHeight, ImageBitmapConfig.Argb8888)
    }
    val canvasObj = remember { androidx.compose.ui.graphics.Canvas(canvasBitmap) }

    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    var currentColor by remember { mutableStateOf(Color.White) }
    var currentStrokeWidth by remember { mutableStateOf(10f) }
    var bitmapTrigger by remember { mutableStateOf(0) }

    var showSettingsMenu by remember { mutableStateOf(false) }
    var showPenSettings by remember { mutableStateOf(false) }
    var isArMode by remember { mutableStateOf(false) }
    var isProcessingAi by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                isArMode = true
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Sketchbook 2025") },
                actions = {
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("AI Magic (Gemini Nano)") },
                                onClick = {
                                    showSettingsMenu = false
                                    scope.launch {
                                        isProcessingAi = true
                                        delay(1500)
                                        // Enhanced AI Magic: Apply a strong "Neon Glow" effect
                                        // We draw a semi-transparent black layer to darken, then draw random neon lines
                                        val paint = Paint().apply {
                                            color = Color.Black.copy(alpha = 0.3f)
                                            blendMode = BlendMode.Darken
                                        }
                                        canvasObj.drawRect(
                                            0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paint
                                        )

                                        // Simulate "AI Hallucination" by adding some random neon sparkles
                                        val sparklePaint = Paint().apply {
                                            strokeWidth = 5f
                                            style = PaintingStyle.Fill
                                            color = Color.Cyan
                                            blendMode = BlendMode.Screen
                                        }
                                        repeat(20) {
                                            val x = (Math.random() * screenWidth).toFloat()
                                            val y = (Math.random() * screenHeight).toFloat()
                                            canvasObj.drawCircle(Offset(x, y), 5f, sparklePaint)
                                        }

                                        bitmapTrigger++
                                        isProcessingAi = false
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isArMode) "Disable AR Mode" else "Enable AR Mode") },
                                onClick = {
                                    showSettingsMenu = false
                                    if (!isArMode && !hasCameraPermission) {
                                        launcher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        isArMode = !isArMode
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        canvasObj.nativeCanvas.drawColor(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.PorterDuff.Mode.CLEAR
                        )
                        bitmapTrigger++
                    },
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                }

                FloatingActionButton(
                    onClick = { showPenSettings = true },
                    containerColor = currentColor
                ) {
                    Icon(
                        Icons.Default.Create,
                        contentDescription = "Pen Tools",
                        tint = if (currentColor.luminance() > 0.5f) Color.Black else Color.White
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isArMode) Color.Transparent else MaterialTheme.colorScheme.background)
        ) {
            // AR Camera Layer
            if (isArMode && hasCameraPermission) {
                ArCameraPreview(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Drawing Canvas Layer
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPathPoints.add(offset)
                            },
                            onDrag = { change, _ ->
                                currentPathPoints.add(change.position)
                            },
                            onDragEnd = {
                                if (currentPathPoints.isNotEmpty()) {
                                    val path = Path().apply {
                                        moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                                        currentPathPoints.drop(1).forEach { lineTo(it.x, it.y) }
                                    }

                                    val paint = Paint().apply {
                                        color = currentColor
                                        strokeWidth = currentStrokeWidth
                                        style = PaintingStyle.Stroke
                                        strokeCap = StrokeCap.Round
                                        strokeJoin = StrokeJoin.Round
                                    }

                                    canvasObj.drawPath(path, paint)
                                    bitmapTrigger++
                                    currentPathPoints.clear()
                                }
                            }
                        )
                    }
            ) {
                // Draw cached bitmap
                bitmapTrigger.let {
                    drawImage(canvasBitmap)
                }

                // Draw active path
                if (currentPathPoints.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                        currentPathPoints.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = currentColor,
                        style = Stroke(
                            width = currentStrokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // AI Processing Overlay
            if (isProcessingAi) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Gemini Nano is dreaming...", color = Color.White)
                    }
                }
            }

            // Pen Settings Modal
            if (showPenSettings) {
                ModalBottomSheet(
                    onDismissRequest = { showPenSettings = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Pen Settings", style = MaterialTheme.typography.titleLarge)

                        Text("Stroke Width: ${currentStrokeWidth.toInt()}")
                        Slider(
                            value = currentStrokeWidth,
                            onValueChange = { currentStrokeWidth = it },
                            valueRange = 1f..50f
                        )

                        Text("Color Palette")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val colors = listOf(
                                Color.White,
                                Color.Red,
                                Color.Blue,
                                Color.Green,
                                Color.Yellow,
                                Color(0xFFBB86FC)
                            )
                            colors.forEach { color ->
                                val isSelected = currentColor == color
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                Color.Transparent
                                        )
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .pointerInput(Unit) {
                                            detectTapGestures {
                                                currentColor = color
                                            }
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Custom Color Wheel")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ColorWheel(
                                modifier = Modifier.size(200.dp),
                                onColorSelected = { currentColor = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    onColorSelected: (Color) -> Unit
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val position = change.position
                    val dx = position.x - center.x
                    val dy = position.y - center.y
                    val angle = (atan2(dy, dx) * 180 / Math.PI + 360) % 360
                    val distance = sqrt(dx * dx + dy * dy)
                    val radius = size.width / 2f

                    if (distance <= radius) {
                        val saturation = (distance / radius).coerceIn(0f, 1f)
                        val hue = angle.toFloat()
                        val color = Color.hsv(hue, saturation, 1f)
                        onColorSelected(color)
                    }
                }
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val angle = (atan2(dy, dx) * 180 / Math.PI + 360) % 360
                    val distance = sqrt(dx * dx + dy * dy)
                    val radius = size.width / 2f

                    if (distance <= radius) {
                        val saturation = (distance / radius).coerceIn(0f, 1f)
                        val hue = angle.toFloat()
                        val color = Color.hsv(hue, saturation, 1f)
                        onColorSelected(color)
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f

        for (angle in 0..360 step 1) {
            val hue = angle.toFloat()
            val color = Color.hsv(hue, 1f, 1f)

            drawArc(
                color = color,
                startAngle = angle.toFloat(),
                sweepAngle = 1.5f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            )
        }

        // Draw white gradient overlay for saturation
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

@Composable
fun ArCameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
        onRelease = {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            if (cameraProviderFuture.isDone) {
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        SketchbookApp()
    }
}
