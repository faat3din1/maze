package my.playground.maze.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import my.playground.maze.codec.DecodeOutcome
import my.playground.maze.codec.MazeCodec
import my.playground.maze.maze.generateMaze
import my.playground.maze.maze.validate
import my.playground.maze.model.Algorithm
import my.playground.maze.model.Difficulty
import my.playground.maze.model.Maze
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val Ink = Color(0xFFF3EDE2)

internal data class Chrome(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
)

internal fun chromeFor(look: Look): Chrome = when (look) {
    Look.SKETCH -> Chrome(
        background = Color(0xFFF6F1E4),
        surface = Color(0xFFE9E1CE),
        ink = Color(0xFF2C2A26),
        accent = Color(0xFF3A342C),
        onAccent = Color(0xFFF6F1E4),
        outline = Color(0xFF6E675C),
    )
    Look.PIXEL -> Chrome(
        background = Color(0xFF14182B),
        surface = Color(0xFF232846),
        ink = Color(0xFFF4F0E6),
        accent = Color(0xFFF9CA24),
        onAccent = Color(0xFF1A1C2C),
        outline = Color(0xFF9AA0C3),
    )
    Look.STONE, Look.EDITOR -> Chrome(
        background = Color(0xFF1C1915),
        surface = Color(0xFF2A2520),
        ink = Ink,
        accent = Color(0xFFE6C07B),
        onAccent = Color(0xFF2A2112),
        outline = Color(0xFFB7A99A),
    )
}

internal val LocalChrome = staticCompositionLocalOf { chromeFor(Look.STONE) }

enum class EditorTool { ROAD, ENTRANCE, EXIT, RIVER, WALL }

sealed interface Screen {
    data object Home : Screen
    data object Setup : Screen
    data class Play(val maze: Maze, val backToEditor: Boolean) : Screen
    data object Editor : Screen
    data class ScanResult(val outcome: DecodeOutcome) : Screen
}

class MazeViewModel : ViewModel() {
    var look by mutableStateOf(Look.STONE)
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var editorWidth by mutableIntStateOf(8)
    var editorHeight by mutableIntStateOf(6)
    var editorMaze by mutableStateOf<Maze?>(null)
        private set
    var editorMapId by mutableIntStateOf(0)
        private set
    var editorTool by mutableStateOf(EditorTool.ROAD)
    var editorNotes by mutableStateOf<List<String>>(emptyList())
        private set
    var showSettings by mutableStateOf(false)
    var shareMaze by mutableStateOf<Maze?>(null)
    var shareNote by mutableStateOf<String?>(null)

    fun open(next: Screen) {
        screen = next
    }

    fun back() {
        screen = when (val current = screen) {
            is Screen.Play -> if (current.backToEditor) Screen.Editor else Screen.Home
            else -> Screen.Home
        }
    }

    fun createBlank() {
        editorMaze = Maze.blank(editorWidth, editorHeight).sealBorder()
        editorMapId += 1
        editorNotes = emptyList()
    }

    fun updateEditor(maze: Maze) {
        editorMaze = maze
        editorNotes = emptyList()
    }

    var lastSpec by mutableStateOf<GenSpec?>(null)
        private set

    fun playGenerated(algorithm: Algorithm, difficulty: Difficulty, width: Int, height: Int, rivers: Int) {
        val level = levelSpec(difficulty, width, height, rivers)
        lastSpec = GenSpec(algorithm, difficulty, level.width, level.height, level.rivers, level.loops)
        val maze = generateMaze(algorithm, level.width, level.height, level.rivers, level.loops)
        screen = Screen.Play(maze, backToEditor = false)
    }

    fun playExisting(maze: Maze, backToEditor: Boolean) {
        lastSpec = null
        screen = Screen.Play(maze, backToEditor)
    }

    fun regenerate(harder: Boolean) {
        val current = lastSpec ?: return
        val next = if (harder) upgrade(current) else current
        lastSpec = next
        val maze = generateMaze(next.algorithm, next.width, next.height, next.rivers, next.loops)
        screen = Screen.Play(maze, backToEditor = false)
    }

    fun checkEditor(then: ((Maze) -> Unit)? = null) {
        val maze = editorMaze?.sealBorder() ?: return
        editorMaze = maze
        val errors = validate(maze)
        if (errors.isNotEmpty()) {
            editorNotes = errors
            return
        }
        editorNotes = listOf("驗證通過")
        then?.invoke(maze)
    }
}

@Composable
fun MazeTheme(look: Look, content: @Composable () -> Unit) {
    val chrome = chromeFor(look)
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = chrome.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = look == Look.SKETCH
    }
    val scheme = if (look == Look.SKETCH) {
        lightColorScheme(
            primary = chrome.accent,
            onPrimary = chrome.onAccent,
            secondary = chrome.accent,
            onSecondary = chrome.onAccent,
            secondaryContainer = chrome.accent,
            onSecondaryContainer = chrome.onAccent,
            background = chrome.background,
            surface = chrome.surface,
            onBackground = chrome.ink,
            onSurface = chrome.ink,
            surfaceVariant = chrome.surface,
            onSurfaceVariant = chrome.ink,
            outline = chrome.outline,
        )
    } else {
        darkColorScheme(
            primary = chrome.accent,
            onPrimary = chrome.onAccent,
            secondary = chrome.accent,
            onSecondary = chrome.onAccent,
            secondaryContainer = chrome.accent,
            onSecondaryContainer = chrome.onAccent,
            background = chrome.background,
            surface = chrome.surface,
            onBackground = chrome.ink,
            onSurface = chrome.ink,
            surfaceVariant = chrome.surface,
            onSurfaceVariant = chrome.ink,
            outline = chrome.outline,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalChrome provides chrome) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = chrome.background,
                contentColor = chrome.ink,
                content = content,
            )
        }
    }
}

@Composable
fun MazeApp(viewModel: MazeViewModel, scanLauncher: androidx.activity.result.ActivityResultLauncher<ScanOptions>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { decodeQrImage(context, uri) }
            viewModel.open(Screen.ScanResult(outcome))
        }
    }
    val dialogOpen = viewModel.showSettings || viewModel.shareMaze != null
    BackHandler(enabled = dialogOpen || viewModel.screen !is Screen.Home) {
        when {
            viewModel.showSettings -> viewModel.showSettings = false
            viewModel.shareMaze != null -> {
                viewModel.shareMaze = null
                viewModel.shareNote = null
            }
            else -> viewModel.back()
        }
    }
    Box(Modifier.fillMaxSize()) {
    when (val screen = viewModel.screen) {
        Screen.Home -> HomeScreen(
            onSettings = { viewModel.showSettings = true },
            onStart = { viewModel.open(Screen.Setup) },
            onEdit = { viewModel.open(Screen.Editor) },
            onScan = {
                scanLauncher.launch(
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("掃描迷宮 QR Code")
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                    },
                )
            },
            onPickImage = {
                pickLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            onPaste = { text -> viewModel.open(Screen.ScanResult(decodeShareText(text))) },
        )
        Screen.Setup -> SetupScreen(
            onBack = { viewModel.back() },
            onPlay = { algorithm, difficulty, width, height, rivers ->
                viewModel.playGenerated(algorithm, difficulty, width, height, rivers)
            },
        )
        is Screen.Play -> PlayScreen(
            maze = screen.maze,
            look = viewModel.look,
            held = viewModel.showSettings || viewModel.shareMaze != null,
            canRegenerate = viewModel.lastSpec != null,
            onBack = { viewModel.back() },
            onSettings = { viewModel.showSettings = true },
            onShare = { note ->
                viewModel.shareMaze = screen.maze
                viewModel.shareNote = note
            },
            onSame = { viewModel.regenerate(harder = false) },
            onHarder = { viewModel.regenerate(harder = true) },
        )
        Screen.Editor -> EditorScreen(
            viewModel = viewModel,
            onSettings = { viewModel.showSettings = true },
            onShare = { maze ->
                viewModel.shareMaze = maze
                viewModel.shareNote = null
            },
        )
        is Screen.ScanResult -> ScanResultScreen(
            outcome = screen.outcome,
            onBack = { viewModel.back() },
            onPlay = { maze -> viewModel.playExisting(maze, backToEditor = false) },
        )
    }
        if (viewModel.showSettings) {
            SettingsDialog(
                look = viewModel.look,
                onLook = { viewModel.look = it },
                onDismiss = { viewModel.showSettings = false },
            )
        }
        viewModel.shareMaze?.let { maze ->
            ShareDialog(
                maze = maze,
                note = viewModel.shareNote,
                onDismiss = {
                    viewModel.shareMaze = null
                    viewModel.shareNote = null
                },
            )
        }
    }
}

fun scanOptionsResult(text: String?): DecodeOutcome {
    if (text.isNullOrEmpty()) return DecodeOutcome.Failure(listOf("沒有掃到 QR"))
    return MazeCodec.decodeQrText(text)
}

fun decodeShareText(raw: String): DecodeOutcome {
    val compact = MazeCodec.normalizeShareText(raw)
    if (compact.isEmpty()) return DecodeOutcome.Failure(listOf("沒有文字"))
    val bytes = try {
        Base64.decode(compact, Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        return DecodeOutcome.Failure(listOf("不是迷宮文字"))
    }
    return MazeCodec.decode(bytes)
}

@Composable
private fun HomeScreen(
    onSettings: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onPaste: (String) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "便便滾滾",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                MazeIconButton(Icons.Filled.Settings, "設定", onSettings)
            }
            Text("傾斜手機，讓便便滾到出口。沒有感應器時可以用手指拖曳。")
            WideButton("開始", onStart)
            WideButton("編輯地圖", onEdit)
            WideButton("掃描 QR Code", onScan)
            WideButton("從圖片讀取", onPickImage)
            PasteMaze(onPaste)
        }
    }
}

@Composable
private fun PasteMaze(onPaste: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("貼上迷宮文字") },
        minLines = 3,
        maxLines = 6,
    )
    WideButton("用文字加入") { onPaste(code) }
}

@Composable
private fun SetupScreen(
    onBack: () -> Unit,
    onPlay: (Algorithm, Difficulty, Int, Int, Int) -> Unit,
) {
    var algorithm by remember { mutableStateOf(Algorithm.DFS) }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var width by remember { mutableIntStateOf(12) }
    var height by remember { mutableIntStateOf(10) }
    var rivers by remember { mutableIntStateOf(4) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MazeIconButton(Icons.Filled.ArrowBack, "返回", onBack)
            Text("開局", style = MaterialTheme.typography.headlineMedium)
            Text("演算法")
            ChipRow {
                AlgorithmChip("DFS", "遞迴回溯", algorithm == Algorithm.DFS) { algorithm = Algorithm.DFS }
                AlgorithmChip("Kruskal", "岔路較均勻", algorithm == Algorithm.KRUSKAL) { algorithm = Algorithm.KRUSKAL }
                AlgorithmChip("Prim", "死路較多", algorithm == Algorithm.PRIM) { algorithm = Algorithm.PRIM }
            }
            Text("難度")
            ChipRow {
                ThemeChip("易", difficulty == Difficulty.EASY) { difficulty = Difficulty.EASY }
                ThemeChip("普通", difficulty == Difficulty.NORMAL) { difficulty = Difficulty.NORMAL }
                ThemeChip("難", difficulty == Difficulty.HARD) { difficulty = Difficulty.HARD }
                ThemeChip("自訂", difficulty == Difficulty.CUSTOM) { difficulty = Difficulty.CUSTOM }
            }
            Text(levelBlurb(difficulty, width, height, rivers))
            if (difficulty == Difficulty.CUSTOM) {
                Text("寬 $width")
                Slider(
                    value = width.toFloat(),
                    onValueChange = { width = it.roundToInt() },
                    valueRange = Maze.MIN_WIDTH.toFloat()..Maze.MAX_WIDTH.toFloat(),
                    steps = Maze.MAX_WIDTH - Maze.MIN_WIDTH - 1,
                )
                Text("高 $height")
                Slider(
                    value = height.toFloat(),
                    onValueChange = { height = it.roundToInt() },
                    valueRange = Maze.MIN_HEIGHT.toFloat()..Maze.MAX_HEIGHT.toFloat(),
                    steps = Maze.MAX_HEIGHT - Maze.MIN_HEIGHT - 1,
                )
                Text("河流 $rivers")
                Slider(
                    value = rivers.toFloat(),
                    onValueChange = { rivers = it.roundToInt() },
                    valueRange = 0f..20f,
                    steps = 19,
                )
            }
            WideButton("生成並開始") { onPlay(algorithm, difficulty, width, height, rivers) }
        }
    }
}

@Composable
private fun SettingsDialog(look: Look, onLook: (Look) -> Unit, onDismiss: () -> Unit) {
    val chrome = LocalChrome.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = chrome.surface, contentColor = chrome.ink),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("設定", style = MaterialTheme.typography.headlineSmall)
                Text("主題")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("石牆", look == Look.STONE) { onLook(Look.STONE) }
                    ThemeChip("素描", look == Look.SKETCH) { onLook(Look.SKETCH) }
                    ThemeChip("像素", look == Look.PIXEL) { onLook(Look.PIXEL) }
                }
                Text("主題會改首頁、開局和編輯器外框。迷宮格子和遊戲畫面維持原樣。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismiss) { Text("關閉") }
            }
        }
    }
}

@Composable
private fun ShareDialog(maze: Maze, note: String?, onDismiss: () -> Unit) {
    val chrome = LocalChrome.current
    val context = LocalContext.current
    val bitmap = remember(maze) { mazeQrBitmap(maze) }
    val payload = remember(maze) { Base64.encodeToString(MazeCodec.encode(maze), Base64.NO_WRAP) }
    var copied by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = chrome.surface, contentColor = chrome.ink),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("分享${maze.width}×${maze.height}迷宮", style = MaterialTheme.typography.headlineSmall)
                if (note != null) Text(note, style = MaterialTheme.typography.titleMedium)
                QrImage(bitmap)
                Text("截圖分享 | APP內掃描加入迷宮", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("迷宮", payload))
                    copied = true
                }) { Text(if (copied) "已複製" else "複製文字") }
                TextButton(onClick = onDismiss) { Text("關閉") }
            }
        }
    }
}

@Composable
private fun ScanResultScreen(
    outcome: DecodeOutcome,
    onBack: () -> Unit,
    onPlay: (Maze) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            when (outcome) {
                is DecodeOutcome.Success -> {
                    Text("掃描成功", style = MaterialTheme.typography.headlineMedium)
                    Text("地圖 ${outcome.maze.width}×${outcome.maze.height}")
                    WideButton("開始遊戲") { onPlay(outcome.maze) }
                }
                is DecodeOutcome.Failure -> {
                    Text("這張圖不能玩", style = MaterialTheme.typography.headlineMedium)
                    outcome.reasons.forEach { Text("・ $it") }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.QrImage(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "便便滾滾 QR Code",
        modifier = Modifier
            .size(220.dp)
            .align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun AlgorithmChip(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
internal fun MazeIconButton(
    image: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val chrome = LocalChrome.current
    val ink = if (selected) chrome.accent else chrome.ink
    val tint = if (enabled) ink else chrome.ink.copy(alpha = 0.38f)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = ink,
            disabledContentColor = ink.copy(alpha = 0.38f),
        ),
    ) {
        Icon(image, contentDescription = description, tint = tint)
    }
}

@Composable
internal fun WideButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private data class LevelSpec(val width: Int, val height: Int, val rivers: Int, val loops: Float)

data class GenSpec(
    val algorithm: Algorithm,
    val difficulty: Difficulty,
    val width: Int,
    val height: Int,
    val rivers: Int,
    val loops: Float,
)

private fun levelSpec(difficulty: Difficulty, width: Int, height: Int, rivers: Int): LevelSpec {
    return when (difficulty) {
        Difficulty.EASY -> LevelSpec(8, 6, 0, 0.12f)
        Difficulty.NORMAL -> LevelSpec(12, 10, 4, 0f)
        Difficulty.HARD -> LevelSpec(16, 16, 10, 0f)
        Difficulty.CUSTOM -> LevelSpec(
            width.coerceIn(Maze.MIN_WIDTH, Maze.MAX_WIDTH),
            height.coerceIn(Maze.MIN_HEIGHT, Maze.MAX_HEIGHT),
            rivers.coerceIn(0, 40),
            0f,
        )
    }
}

private fun upgrade(spec: GenSpec): GenSpec {
    return when (spec.difficulty) {
        Difficulty.EASY -> preset(spec.algorithm, Difficulty.NORMAL)
        Difficulty.NORMAL -> preset(spec.algorithm, Difficulty.HARD)
        Difficulty.HARD, Difficulty.CUSTOM -> {
            val width = (spec.width + 2).coerceAtMost(Maze.MAX_WIDTH)
            val height = (spec.height + 2).coerceAtMost(Maze.MAX_HEIGHT)
            val rivers = (spec.rivers + 2).coerceAtMost(40)
            spec.copy(difficulty = Difficulty.CUSTOM, width = width, height = height, rivers = rivers, loops = 0f)
        }
    }
}

private fun preset(algorithm: Algorithm, difficulty: Difficulty): GenSpec {
    val level = levelSpec(difficulty, 0, 0, 0)
    return GenSpec(algorithm, difficulty, level.width, level.height, level.rivers, level.loops)
}

private fun levelBlurb(difficulty: Difficulty, width: Int, height: Int, rivers: Int): String {
    val spec = levelSpec(difficulty, width, height, rivers)
    val loops = if (spec.loops > 0f) "，有環路" else "，完美迷宮"
    return "${spec.width}×${spec.height}，河流 ${spec.rivers}$loops"
}
