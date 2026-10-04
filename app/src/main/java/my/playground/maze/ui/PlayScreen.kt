package my.playground.maze.ui

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import my.playground.maze.maze.solutionPath
import my.playground.maze.model.Maze
import my.playground.maze.physics.MarbleSim

@Composable
fun PlayScreen(
    maze: Maze,
    look: Look,
    held: Boolean,
    canRegenerate: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onShare: (String?) -> Unit,
    onSame: () -> Unit,
    onHarder: () -> Unit,
) {
    val themeChrome = LocalChrome.current
    val view = LocalView.current
    DisposableEffect(themeChrome) {
        val window = (view.context as Activity).window
        window.statusBarColor = Color(0xFF1C1915).toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        onDispose {
            window.statusBarColor = themeChrome.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                themeChrome.background.luminance() > 0.5f
        }
    }
    CompositionLocalProvider(LocalChrome provides chromeFor(Look.STONE)) {
        PlayScreenContent(maze, look, held, canRegenerate, onBack, onSettings, onShare, onSame, onHarder)
    }
}

@Composable
private fun PlayScreenContent(
    maze: Maze,
    look: Look,
    held: Boolean,
    canRegenerate: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onShare: (String?) -> Unit,
    onSame: () -> Unit,
    onHarder: () -> Unit,
) {
    val context = LocalContext.current
    val sim = remember(maze) { MarbleSim(maze) }
    val solution = remember(maze) { solutionPath(maze) }
    var showHint by remember(maze) { mutableStateOf(false) }
    var usedHint by remember(maze) { mutableStateOf(false) }
    var showHelp by remember(maze) { mutableStateOf(true) }
    var elapsed by remember(maze) { mutableFloatStateOf(0f) }
    val tilt = remember { Tilt() }
    var paused by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var splash by remember { mutableFloatStateOf(0f) }
    var seenRiver by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val sizeState = rememberUpdatedState(boardSize)
    val draggingState = rememberUpdatedState(dragging)
    val dragXState = rememberUpdatedState(dragX)
    val dragYState = rememberUpdatedState(dragY)
    val pausedState = rememberUpdatedState(paused)
    val heldState = rememberUpdatedState(held)

    DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val rotation = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            private val matrix = FloatArray(9)
            private val mapped = FloatArray(9)
            override fun onSensorChanged(event: SensorEvent) {
                val display = displayRotation(context)
                val raw = if (
                    event.sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
                    event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR
                ) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    val used = remap(matrix, mapped, display)
                    used[2] to -used[5]
                } else {
                    val (x, y) = rotatePair(event.values[0], event.values[1], display)
                    -x / SensorManager.GRAVITY_EARTH to y / SensorManager.GRAVITY_EARTH
                }
                tilt.x = tilt.x * 0.7f + raw.first * 0.3f
                tilt.y = tilt.y * 0.7f + raw.second * 0.3f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val sensor = rotation ?: accelerometer
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    androidx.compose.runtime.LaunchedEffect(maze) {
        delay(2000)
        showHelp = false
    }

    androidx.compose.runtime.LaunchedEffect(maze) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { time ->
                val dt = if (last == 0L) 0f else ((time - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = time
                if (!pausedState.value && !heldState.value && !sim.won) {
                    val pushX = if (draggingState.value) dragXState.value else 0f
                    elapsed += dt
                    val pushY = if (draggingState.value) dragYState.value else 0f
                    sim.ax = tilt.x * 22f + pushX
                    sim.ay = tilt.y * 22f + pushY
                    sim.step(dt)
                    if (sim.riverFlash != seenRiver) {
                        seenRiver = sim.riverFlash
                        splash = 1f
                    } else {
                        splash = (splash - dt * 1.4f).coerceAtLeast(0f)
                    }
                }
                tick += 1
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1915))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MazeIconButton(Icons.Filled.ArrowBack, "返回", onBack)
            Spacer(Modifier.weight(1f))
            MazeIconButton(Icons.Filled.Share, "分享", { onShare(shareNote(sim.won, elapsed, usedHint)) })
            MazeIconButton(
                Icons.Filled.Lightbulb,
                "提示",
                {
                    if (!showHint) usedHint = true
                    showHint = !showHint
                },
                enabled = solution != null,
                selected = showHint,
            )
            MazeIconButton(Icons.Filled.Settings, "設定", onSettings)
            MazeIconButton(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                if (paused) "繼續" else "暫停",
                { paused = !paused },
            )
            MazeIconButton(Icons.Filled.Refresh, "重開", {
                sim.reset()
                splash = 0f
                paused = false
                elapsed = 0f
                usedHint = false
                showHint = false
            })
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .onSizeChanged { boardSize = it }
                .pointerInput(maze) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, delta ->
                            val width = sizeState.value.width.toFloat().coerceAtLeast(1f)
                            val cell = fitBoard(width, sizeState.value.height.toFloat(), maze.width, maze.height).width / maze.width
                            dragX = delta.x / cell * 48f
                            dragY = delta.y / cell * 48f
                            change.consume()
                        },
                    )
                },
        ) {
            val frame = tick
            Canvas(Modifier.fillMaxSize()) {
                if (frame < 0) return@Canvas
                val dest = fitBoard(size.width, size.height, maze.width, maze.height)
                val hint = if (showHint) {
                    val column = sim.x.toInt().coerceIn(0, maze.width - 1)
                    val row = sim.y.toInt().coerceIn(0, maze.height - 1)
                    solutionPath(maze, maze.index(column, row))
                } else {
                    null
                }
                drawMazeScene(
                    maze,
                    look,
                    dest,
                    sim.x,
                    sim.y,
                    sim.radius,
                    solution = hint,
                )
            }
            if (splash > 0f && !sim.won) {
                Card(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xF02A2520).copy(alpha = splash.coerceIn(0.2f, 1f)),
                        contentColor = Ink,
                    ),
                ) {
                    Text(
                        "被冲走了，回到入口",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (showHelp) {
                Text(
                    "傾斜手機，讓便便滾到出口。或用手指拖曳。",
                    color = Ink,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${maze.width}×${maze.height}", color = Ink, modifier = Modifier.weight(1f))
                    Text(formatTime(elapsed), color = Ink, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (paused && !sim.won) {
            StatusCard(title = "暫停") {
                Button(onClick = { paused = false }, modifier = Modifier.fillMaxWidth()) { Text("繼續") }
            }
        }
        if (sim.won) {
            StatusCard(title = "過關", detail = formatTime(elapsed)) {
                Button(
                    onClick = {
                        sim.reset()
                        splash = 0f
                        paused = false
                        elapsed = 0f
                        usedHint = false
                        showHint = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("再玩一次") }
                if (canRegenerate) {
                    Button(onClick = onSame, modifier = Modifier.fillMaxWidth()) { Text("同難度新地圖") }
                    Button(onClick = onHarder, modifier = Modifier.fillMaxWidth()) { Text("升級難度") }
                }
                Button(
                    onClick = { onShare(shareNote(sim.won, elapsed, usedHint)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("分享") }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }
        }
    }
    }
}

private fun shareNote(won: Boolean, seconds: Float, usedHint: Boolean): String? {
    if (!won) return null
    val total = seconds.toInt().coerceAtLeast(0)
    val clock = "%d分 %02d 秒".format(total / 60, total % 60)
    val line = "我用了 $clock 通過了這關"
    return if (usedHint) "$line（開了hints）" else line
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun StatusCard(title: String, detail: String? = null, actions: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2520), contentColor = Ink),
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                if (detail != null) Text(detail, style = MaterialTheme.typography.titleLarge)
                actions()
            }
        }
    }
}

private class Tilt {
    @Volatile var x = 0f
    @Volatile var y = 0f
}

private fun displayRotation(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
}

private fun remap(input: FloatArray, output: FloatArray, rotation: Int): FloatArray {
    val ok = when (rotation) {
        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
            input, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, output,
        )
        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
            input, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, output,
        )
        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
            input, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, output,
        )
        else -> SensorManager.remapCoordinateSystem(
            input, SensorManager.AXIS_X, SensorManager.AXIS_Y, output,
        )
    }
    if (!ok) input.copyInto(output)
    return output
}

private fun rotatePair(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
    Surface.ROTATION_90 -> -y to x
    Surface.ROTATION_180 -> -x to -y
    Surface.ROTATION_270 -> y to -x
    else -> x to y
}
