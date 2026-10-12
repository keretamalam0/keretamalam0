package id.keretamalam.cafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import id.keretamalam.cafe.ui.navigation.CafeFlowAppShell
import id.keretamalam.cafe.ui.theme.KeretaMalamTheme
import id.keretamalam.cafe.viewmodel.PosViewModel

class MainActivity : ComponentActivity() {

    private val posViewModel: PosViewModel by viewModels()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Status izin Bluetooth diperbarui untuk pemindaian printer ESC/POS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!posViewModel.printerManager.hasBluetoothPermissions()) {
            bluetoothPermissionLauncher.launch(
                posViewModel.printerManager.requiredRuntimePermissions()
            )
        }

        setContent {
            val uiState by posViewModel.uiState.collectAsState()

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
