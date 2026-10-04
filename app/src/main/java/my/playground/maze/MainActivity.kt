package my.playground.maze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import my.playground.maze.ui.MazeApp
import my.playground.maze.ui.MazeTheme
import my.playground.maze.ui.MazeViewModel
import my.playground.maze.ui.Screen
import my.playground.maze.ui.scanOptionsResult

class MainActivity : ComponentActivity() {
    private val pendingScan = mutableStateOf<String?>(null)
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        pendingScan.value = result.contents
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MazeViewModel = viewModel()
            val scanned = pendingScan.value
            LaunchedEffect(scanned) {
                if (scanned != null) {
                    viewModel.open(Screen.ScanResult(scanOptionsResult(scanned)))
                    pendingScan.value = null
                }
            }
            MazeTheme(viewModel.look) {
                MazeApp(viewModel, scanLauncher)
            }
        }
    }
}
