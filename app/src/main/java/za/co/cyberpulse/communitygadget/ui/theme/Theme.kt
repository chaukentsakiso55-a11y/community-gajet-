package za.co.cyberpulse.communitygadget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CommunityColors = darkColorScheme(
    primary = Color(0xFF29E7A5),
    onPrimary = Color(0xFF001F16),
    secondary = Color(0xFFFFC857),
    tertiary = Color(0xFF5ED8FF),
    background = Color(0xFF07111B),
    onBackground = Color(0xFFE6F4F1),
    surface = Color(0xFF0B1B27),
    onSurface = Color(0xFFE6F4F1),
    surfaceVariant = Color(0xFF122B38),
    onSurfaceVariant = Color(0xFFAFC9C7),
    error = Color(0xFFFF5D68),
    onError = Color.White
)

@Composable
fun CommunityGadgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CommunityColors, content = content)
}
