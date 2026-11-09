package io.github.klark231.mh4ubuilder

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF8F4C00), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2), onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF735943), tertiary = Color(0xFF5B6237),
    background = Color(0xFFFFF8F5), surface = Color(0xFFFFF8F5),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB77C), onPrimary = Color(0xFF4C2700),
    primaryContainer = Color(0xFF6D3900), onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE3BFA5), tertiary = Color(0xFFC4CB97),
    background = Color(0xFF1A120D), surface = Color(0xFF1A120D),
)

@Composable
fun BuilderTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
