package com.example.gl

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Custom GLSurfaceView wrapper for 3D Cyberpunk Character Model rendering.
 */
class CyberCharacterGLSurfaceView(context: Context) : GLSurfaceView(context) {
    val characterRenderer = CyberCharacterRenderer()

    init {
        setEGLContextClientVersion(3) // OpenGL ES 3.0
        setRenderer(characterRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

/**
 * Jetpack Compose Composable for embedding the 3D Character Renderer in UI screens.
 */
@Composable
fun CyberCharacterGLView(
    modifier: Modifier = Modifier,
    hueR: Float = 0.0f,
    hueG: Float = 1.0f,
    hueB: Float = 0.85f,
    variant: CharacterVariant = CharacterVariant.GENERIC
) {
    AndroidView(
        factory = { ctx ->
            CyberCharacterGLSurfaceView(ctx).apply {
                characterRenderer.activeHueR = hueR
                characterRenderer.activeHueG = hueG
                characterRenderer.activeHueB = hueB
                characterRenderer.variant = variant
            }
        },
        update = { view ->
            view.characterRenderer.activeHueR = hueR
            view.characterRenderer.activeHueG = hueG
            view.characterRenderer.activeHueB = hueB
            view.characterRenderer.variant = variant
        },
        modifier = modifier
    )
}
