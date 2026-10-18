package id.rudissp.smania

import android.graphics.Path as AndroidPath
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix as ComposeMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star


class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val composeMatrix = ComposeMatrix().apply {
            // Pindahkan translate ke atas sebelum scale
            translate(size.width / 2f, size.height / 2f)
            scale(size.width / 2f, size.height / 2f)
        }

        val androidPath = AndroidPath()
        val cubics = morph.asCubics(percentage)
        if (cubics.isNotEmpty()) {
            val first = cubics[0]
            androidPath.moveTo(first.anchor0X, first.anchor0Y)
            for (cubic in cubics) {
                androidPath.cubicTo(
                    cubic.control0X, cubic.control0Y,
                    cubic.control1X, cubic.control1Y,
                    cubic.anchor1X, cubic.anchor1Y
                )
            }
            androidPath.close()
        }

        val composePath = androidPath.asComposePath()
        composePath.transform(composeMatrix)
        return Outline.Generic(composePath)
    }
}

/**
 * LoadingIndicator resmi sesuai dengan desain Material 3 Expressive UI.
 * Melakukan animasi morphing/perubahan bentuk antar poligon/bintang secara kontinu
 * sebagaimana didokumentasikan di developer.android.com.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val shapeA = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = 1f,
            innerRadius = 0.7f,
            rounding = CornerRounding(0.2f)
        )
    }
    val shapeB = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = 1f,
            innerRadius = 0.4f,
            rounding = CornerRounding(0.4f)
        )
    }
    val morph = remember { Morph(shapeA, shapeB) }

    val infiniteTransition = rememberInfiniteTransition(label = "LoadingMorphAnimation")
    val animatedProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morphProgress"
    )
    val animatedRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphRotation"
    )

    val morphShape = remember(morph, animatedProgress.value) {
        MorphPolygonShape(morph, animatedProgress.value)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = animatedRotation.value
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(morphShape)
                .background(color)
        )
    }
}