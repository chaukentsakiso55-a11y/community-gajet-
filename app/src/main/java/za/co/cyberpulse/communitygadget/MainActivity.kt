package za.co.cyberpulse.communitygadget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import za.co.cyberpulse.communitygadget.permissions.AppPermissions
import za.co.cyberpulse.communitygadget.ui.AppViewModel
import za.co.cyberpulse.communitygadget.ui.CommunityGadgetApp
import za.co.cyberpulse.communitygadget.ui.theme.CommunityGadgetTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val permissionsReady = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsReady.value = AppPermissions.allGranted(this)
        if (permissionsReady.value) viewModel.startMesh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        permissionsReady.value = AppPermissions.allGranted(this)
        setContent {
            CommunityGadgetTheme {
                CommunityGadgetApp(
                    viewModel = viewModel,
                    permissionsReady = permissionsReady.value,
                    requestPermissions = { permissionLauncher.launch(AppPermissions.required()) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsReady.value = AppPermissions.allGranted(this)
        if (permissionsReady.value && viewModel.config.value != null) viewModel.startMesh()
    }
}
