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

    // Drawing State
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx().toInt() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx().toInt() }

    // We store strokes as data to allow "AI" manipulation (smoothing/styling)
    data class Stroke(
        val points: List<Offset>,
        val color: Color,
        val width: Float,
        val isNeon: Boolean = false
    )

    val strokes = remember { mutableStateListOf<Stroke>() }
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    
    var currentColor by remember { mutableStateOf(Color.White) }
    var currentStrokeWidth by remember { mutableStateOf(10f) }

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

    // Helper to smooth lines (Simple averaging "AI")
    fun smoothPoints(points: List<Offset>): List<Offset> {
        if (points.size < 3) return points
        val smoothed = mutableListOf<Offset>()
        smoothed.add(points.first())
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]
            // Average neighbors
            smoothed.add(
                Offset(
                    (prev.x + curr.x + next.x) / 3f,
                    (prev.y + curr.y + next.y) / 3f
                )
            )
        }
        smoothed.add(points.last())
        return smoothed
    }

    // Helper to connect gaps between strokes ("Shape Recognition" helper)
    fun connectGaps(strokes: List<Stroke>): List<Stroke> {
        val threshold = 60f // Distance threshold to snap points
        // Create mutable copies of points so we can modify them
        val mutableStrokesPoints = strokes.map { it.points.toMutableList() }

        for (i in mutableStrokesPoints.indices) {
            val pointsA = mutableStrokesPoints[i]
            if (pointsA.isEmpty()) continue

            for (j in i + 1 until mutableStrokesPoints.size) {
                val pointsB = mutableStrokesPoints[j]
                if (pointsB.isEmpty()) continue

                val headA = pointsA.first()
                val tailA = pointsA.last()
                val headB = pointsB.first()
                val tailB = pointsB.last()

                // Check 4 combinations of endpoints
                // Tail A -> Head B
                if ((tailA - headB).getDistance() < threshold) {
                    val mid = (tailA + headB) / 2f
                    pointsA[pointsA.lastIndex] = mid
                    pointsB[0] = mid
                }
                // Tail A -> Tail B
                if ((tailA - tailB).getDistance() < threshold) {
                    val mid = (tailA + tailB) / 2f
                    pointsA[pointsA.lastIndex] = mid
                    pointsB[pointsB.lastIndex] = mid
                }
                // Head A -> Head B
                if ((headA - headB).getDistance() < threshold) {
                    val mid = (headA + headB) / 2f
                    pointsA[0] = mid
                    pointsB[0] = mid
                }
                // Head A -> Tail B
                if ((headA - tailB).getDistance() < threshold) {
                    val mid = (headA + tailB) / 2f
                    pointsA[0] = mid
                    pointsB[pointsB.lastIndex] = mid
                }
            }
        }

        // Return new strokes with modified points
        return strokes.zip(mutableStrokesPoints) { stroke, newPoints ->
            stroke.copy(points = newPoints)
        }
    }

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
                                text = { Text("AI Magic: Connect & Glow") },
                                onClick = {
                                    showSettingsMenu = false
                                    scope.launch {
                                        isProcessingAi = true
                                        delay(1000) // Simulate "Thinking"
                                        
                                        // 1. Connect Gaps (Shape Recognition)
                                        val connectedStrokes = connectGaps(strokes)
                                        
                                        // 2. Smooth & Neonify
                                        val enhancedStrokes = connectedStrokes.map { stroke ->
                                            stroke.copy(
                                                points = smoothPoints(stroke.points),
                                                color = Color.Cyan, // Cyberpunk Neon
                                                width = stroke.width * 0.8f, // Refine width
                                                isNeon = true
                                            )
                                        }
                                        strokes.clear()
                                        strokes.addAll(enhancedStrokes)
                                        
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
                        strokes.clear()
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
                                    strokes.add(
                                        Stroke(
                                            points = currentPathPoints.toList(),
                                            color = currentColor,
                                            width = currentStrokeWidth
                                        )
                                    )
                                    currentPathPoints.clear()
                                }
                            }
                        )
                    }
            ) {
                // Draw all recorded strokes
                strokes.forEach { stroke ->
                    val path = Path().apply {
                        if (stroke.points.isNotEmpty()) {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                    }
                    
                    // If "Neon" (AI Enhanced), draw a glow effect
                    if (stroke.isNeon) {
                        drawPath(
                            path = path,
                            color = stroke.color.copy(alpha = 0.5f),
                            style = Stroke(
                                width = stroke.width * 2.5f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Draw active path (currently being drawn)
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
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.Cyan)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Gemini Nano is connecting the dots...", 
                            color = Color.Cyan,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "(Sketch-to-Image requires Cloud API)", 
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
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
                                selectedColor = currentColor,
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
    selectedColor: Color,
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
        
        // Draw selection indicator
        // Convert selectedColor to HSV to find position
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        
        // Calculate position from HSV
        // Angle in radians
        val angleRad = Math.toRadians(hue.toDouble())
        val dist = saturation * radius
        val x = center.x + dist * kotlin.math.cos(angleRad)
        val y = center.y + dist * kotlin.math.sin(angleRad)
        
        // Draw indicator circle
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = Offset(x.toFloat(), y.toFloat()),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = selectedColor,
            radius = 6.dp.toPx(),
            center = Offset(x.toFloat(), y.toFloat())
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
