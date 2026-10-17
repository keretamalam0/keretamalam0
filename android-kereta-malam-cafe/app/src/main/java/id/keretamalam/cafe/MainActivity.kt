package id.keretamalam.cafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import id.keretamalam.cafe.ui.navigation.CafeFlowAppShell
import id.keretamalam.cafe.ui.theme.KeretaMalamTheme
import id.keretamalam.cafe.viewmodel.PosViewModel

class MainActivity : ComponentActivity() {

    private val posViewModel: PosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by posViewModel.uiState.collectAsState()

            // Keep system-bar icon contrast tied to the app theme on Android 13+,
            // where edge-to-edge is enforced for apps targeting API 35.
            SideEffect {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !uiState.darkTheme
                    isAppearanceLightNavigationBars = !uiState.darkTheme
                }
            }

            KeretaMalamTheme(
                darkTheme = uiState.darkTheme,
                uiScalePercent = uiState.uiScalePercent
            ) {
                CafeFlowAppShell(
                    viewModel = posViewModel,
                    uiState = uiState
                )
            }
        }
    }
}
