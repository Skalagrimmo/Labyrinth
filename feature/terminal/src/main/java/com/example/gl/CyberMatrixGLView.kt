package com.example.gl

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Jetpack Compose wrapper component for the 3D Cyberpunk Matrix OpenGL ES 3.0 GLSurfaceView.
 */
@Composable
fun CyberMatrixGLView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glSurfaceView = remember(context) {
        GLSurfaceView(context).apply {
            // Require OpenGL ES 3.0 context
            setEGLContextClientVersion(3)
            val renderer = CyberMatrixRenderer()
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(glSurfaceView) {
        glSurfaceView.onResume()
        onDispose {
            glSurfaceView.onPause()
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier
    )
}
