package id.rudissp.smania.buku

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Komponen Indikator Progres Bergelombang (LinearWavyProgressIndicator)
 * Gaya Expressive Material 3 UI.
 */
@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier.fillMaxWidth().height(12.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeWidth: Dp = 4.dp,
    wavelength: Dp = 24.dp,
    waveAmplitude: Dp = 3.dp
) {
    val currentProgress = progress().coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }
    val wavelengthPx = with(LocalDensity.current) { wavelength.toPx() }
    val waveAmplitudePx = with(LocalDensity.current) { waveAmplitude.toPx() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // 1. Gambar jalur track dasar
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        // 2. Gambar jalur aktif bergelombang (Wavy Progress)
        val activeWidth = width * currentProgress
        if (activeWidth > 0f) {
            val wavePath = Path()
            val stepPx = 2f
            var x = 0f

            wavePath.moveTo(0f, centerY + sin(-phase) * waveAmplitudePx)

            while (x <= activeWidth) {
                val y = centerY + sin((x / wavelengthPx) * (2 * PI).toFloat() - phase) * waveAmplitudePx
                wavePath.lineTo(x, y)
                x += stepPx
            }

            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

@Composable
fun LinearWavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier.fillMaxWidth().height(12.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeWidth: Dp = 4.dp,
    wavelength: Dp = 24.dp,
    waveAmplitude: Dp = 3.dp
) {
    LinearWavyProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        wavelength = wavelength,
        waveAmplitude = waveAmplitude
    )
}