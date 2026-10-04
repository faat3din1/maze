package my.playground.maze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import my.playground.maze.model.Dir
import my.playground.maze.model.Maze
import my.playground.maze.model.Tile
import kotlinx.coroutines.delay
import kotlin.math.floor

@Composable
fun EditorScreen(
    viewModel: MazeViewModel,
    onSettings: () -> Unit,
    onShare: (Maze) -> Unit,
) {
    val maze = viewModel.editorMaze
    var scale by remember(viewModel.editorMapId) { mutableFloatStateOf(1f) }
    var offset by remember(viewModel.editorMapId) { mutableStateOf(Offset.Zero) }
    Column(
        Modifier
            .fillMaxSize()
            .background(LocalChrome.current.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MazeIconButton(Icons.Filled.ArrowBack, "返回", viewModel::back)
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stepper("寬", viewModel.editorWidth, Maze.MIN_WIDTH, Maze.MAX_WIDTH) { viewModel.editorWidth = it }
                Stepper("高", viewModel.editorHeight, Maze.MIN_HEIGHT, Maze.MAX_HEIGHT) { viewModel.editorHeight = it }
            }
            MazeIconButton(Icons.Filled.Add, "建立 ${viewModel.editorWidth}×${viewModel.editorHeight}", viewModel::createBlank)
            MazeIconButton(Icons.Filled.Settings, "設定", onSettings)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF241F1B)),
        ) {
            if (maze == null) {
                Text(
                    "先設定大小，再按建立。",
                    modifier = Modifier.align(Alignment.Center),
                    color = Ink,
                )
            } else {
                EditorBoard(
                    maze = maze,
                    mapId = viewModel.editorMapId,
                    tool = viewModel.editorTool,
                    scale = scale,
                    offset = offset,
                    onTransform = { zoom, pan ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        scale = next
                        offset = if (next <= 1.01f) {
                            scale = 1f
                            Offset.Zero
                        } else {
                            val limit = 800f * next
                            Offset(
                                (offset.x + pan.x).coerceIn(-limit, limit),
                                (offset.y + pan.y).coerceIn(-limit, limit),
                            )
                        }
                    },
                    onMaze = { viewModel.updateEditor(it) },
                )
                Text(
                    "${(scale * 100).toInt()}%  ·  單指塗色，雙指縮放同移動",
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Ink,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (viewModel.editorNotes.isNotEmpty()) {
                    Card(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .heightIn(max = 88.dp)
                            .verticalScroll(rememberScrollState()),
                        colors = CardDefaults.cardColors(containerColor = Color(0xF02A2520), contentColor = Ink),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            viewModel.editorNotes.forEach { note ->
                                Text(note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolButton("道路", Color(0xFFF4F1EA), viewModel.editorTool == EditorTool.ROAD) {
                viewModel.editorTool = EditorTool.ROAD
            }
            ToolButton("入口", Color(0xFF9CCC65), viewModel.editorTool == EditorTool.ENTRANCE) {
                viewModel.editorTool = EditorTool.ENTRANCE
            }
            ToolButton("出口", Color(0xFFFFD54F), viewModel.editorTool == EditorTool.EXIT) {
                viewModel.editorTool = EditorTool.EXIT
            }
            ToolButton("河流", Color(0xFF4FC3F7), viewModel.editorTool == EditorTool.RIVER) {
                viewModel.editorTool = EditorTool.RIVER
            }
            ToolButton("障礙物", Color(0xFF4E342E), viewModel.editorTool == EditorTool.WALL) {
                viewModel.editorTool = EditorTool.WALL
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MazeIconButton(Icons.Filled.Check, "驗證", { viewModel.checkEditor() }, maze != null)
            MazeIconButton(
                Icons.Filled.PlayArrow,
                "試玩",
                { viewModel.checkEditor { viewModel.playExisting(it, backToEditor = true) } },
                maze != null,
            )
            MazeIconButton(Icons.Filled.Share, "分享", { viewModel.checkEditor(onShare) }, maze != null)
        }
    }
}

@Composable
private fun EditorBoard(
    maze: Maze,
    mapId: Int,
    tool: EditorTool,
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit,
    onMaze: (Maze) -> Unit,
) {
    val mazeState = rememberUpdatedState(maze)
    val toolState = rememberUpdatedState(tool)
    val scaleState = rememberUpdatedState(scale)
    val offsetState = rememberUpdatedState(offset)
    val transformState = rememberUpdatedState(onTransform)
    val mazeCallback = rememberUpdatedState(onMaze)
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(mapId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var multi = false
                    fun paint(position: Offset) {
                        paintAt(
                            position,
                            size.width.toFloat(),
                            size.height.toFloat(),
                            mazeState.value,
                            toolState.value,
                            scaleState.value,
                            offsetState.value,
                            mazeCallback.value,
                        )
                    }
                    paint(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            multi = true
                            val a = pressed[0]
                            val b = pressed[1]
                            val oldLen = (a.previousPosition - b.previousPosition).getDistance().coerceAtLeast(1f)
                            val newLen = (a.position - b.position).getDistance().coerceAtLeast(1f)
                            val pan = ((a.position + b.position) / 2f) - ((a.previousPosition + b.previousPosition) / 2f)
                            transformState.value(newLen / oldLen, pan)
                            pressed.forEach { it.consume() }
                        } else if (!multi && pressed.size == 1) {
                            val change = pressed[0]
                            if (change.positionChange() != Offset.Zero) paint(change.position)
                            change.consume()
                        }
                        if (pressed.isEmpty()) break
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        val dest = fitBoard(size.width, size.height, maze.width, maze.height)
        drawMazeScene(maze, Look.EDITOR, dest)
    }
}

private fun paintAt(
    position: Offset,
    viewWidth: Float,
    viewHeight: Float,
    maze: Maze,
    tool: EditorTool,
    scale: Float,
    offset: Offset,
    onMaze: (Maze) -> Unit,
) {
    val dest = fitBoard(viewWidth, viewHeight, maze.width, maze.height)
    val center = Offset(viewWidth / 2f, viewHeight / 2f)
    val local = center + (position - offset - center) / scale
    val cellW = dest.width / maze.width
    val cellH = dest.height / maze.height
    val gx = (local.x - dest.left) / cellW
    val gy = (local.y - dest.top) / cellH
    val c = floor(gx).toInt()
    val r = floor(gy).toInt()
    if (c !in 0 until maze.width || r !in 0 until maze.height) return
    val next = if (tool == EditorTool.WALL) {
        val fx = gx - c
        val fy = gy - r
        val dir = listOf(Dir.UP to fy, Dir.DOWN to 1f - fy, Dir.LEFT to fx, Dir.RIGHT to 1f - fx)
            .minBy { it.second }
        if (dir.second > 0.28f) return
        maze.toggleEdge(c, r, dir.first)
    } else {
        val type = when (tool) {
            EditorTool.ROAD -> Tile.ROAD
            EditorTool.ENTRANCE -> Tile.ENTRANCE
            EditorTool.EXIT -> Tile.EXIT
            EditorTool.RIVER -> Tile.RIVER
            EditorTool.WALL -> Tile.ROAD
        }
        maze.paintType(maze.index(c, r), type)
    }
    if (next != maze) onMaze(next)
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val ink = LocalChrome.current.ink
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ink)
        HoldStep("－", value > min) { onChange((value - 1).coerceAtLeast(min)) }
        Text("$value", color = ink)
        HoldStep("＋", value < max) { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun HoldStep(label: String, enabled: Boolean, onStep: () -> Unit) {
    val ink = LocalChrome.current.ink
    val step = rememberUpdatedState(onStep)
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding, enabled) {
        if (!holding || !enabled) return@LaunchedEffect
        delay(380)
        while (holding && enabled) {
            step.value()
            delay(70)
        }
    }
    Text(
        label,
        color = if (enabled) ink else ink.copy(alpha = 0.38f),
        modifier = Modifier
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = { if (enabled) step.value() },
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    },
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun ToolButton(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val chrome = LocalChrome.current
    val borderColor = if (selected) chrome.accent else chrome.outline
    Row(
        Modifier
            .border(if (selected) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = chrome.ink)
    }
}
