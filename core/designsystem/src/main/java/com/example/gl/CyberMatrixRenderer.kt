package com.example.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3.0 GLSurfaceView.Renderer for rendering a 3D Cyberpunk Matrix environment.
 * Features:
 * - GLSL ES 3.0 Shaders (#version 300 es)
 * - Interleaved Vertex Attributes & Compact Data Types (GL_SHORT, GL_BYTE, GL_UNSIGNED_BYTE)
 *   for optimal GPU cache performance and reduced memory bandwidth.
 * - 3D Perspective Projection & View Matrix Transformations
 * - 3D Cyber Grid Floor with glowing neon scanlines & distance fog
 * - Animated 3D Cyber Data Cubes & Nodes with vertex attribute buffers (VBO/VAO)
 * - Dynamic lighting & pulsing ambient color animations
 */
class CyberMatrixRenderer : GLSurfaceView.Renderer {

    // MVP Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    // Shader Programs
    private var gridShaderProgram: Int = 0
    private var cubeShaderProgram: Int = 0
    private var overlayShaderProgram: Int = 0

    // Handles for Grid Shader
    private var gridMvpMatrixHandle: Int = -1
    private var gridTimeHandle: Int = -1
    private var gridPositionHandle: Int = -1

    // Handles for Cube Shader
    private var cubeMvpMatrixHandle: Int = -1
    private var cubeNormalMatrixHandle: Int = -1
    private var cubeModelMatrixHandle: Int = -1
    private var cubeTimeHandle: Int = -1
    private var cubePositionHandle: Int = -1
    private var cubeNormalHandle: Int = -1
    private var cubeColorHandle: Int = -1

    // Handles for Digital Rain & Scanline Overlay Shader
    private var overlayTimeHandle: Int = -1
    private var overlayResolutionHandle: Int = -1
    private var overlayRainColorHandle: Int = -1

    // Vertex Array Objects & Vertex Buffer Objects
    private val vao = IntArray(3)
    private val vbo = IntArray(4)
    private val ebo = IntArray(1)

    // Screen Dimensions
    private var screenWidth: Float = 1080.0f
    private var screenHeight: Float = 1920.0f

    // Data Sizes
    private var gridVertexCount: Int = 0
    private var cubeIndexCount: Int = 0

    private var startTimeMs: Long = SystemClock.uptimeMillis()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set deep cyberpunk background color (dark cyan/navy)
        GLES30.glClearColor(0.02f, 0.04f, 0.08f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        initShaders()
        setupGridBuffers()
        setupCubeBuffers()
        setupOverlayBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        screenWidth = width.toFloat()
        screenHeight = height.coerceAtLeast(1).toFloat()

        val ratio: Float = screenWidth / screenHeight
        // Set up 3D perspective frustum
        Matrix.perspectiveM(projectionMatrix, 0, 60.0f, ratio, 0.1f, 100.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val timeSeconds = (SystemClock.uptimeMillis() - startTimeMs) / 1000.0f

        // Camera position: Looking into the cyber grid horizon
        val cameraX = Math.sin(timeSeconds * 0.2).toFloat() * 2.0f
        val cameraY = 3.5f
        val cameraZ = 8.0f
        Matrix.setLookAtM(
            viewMatrix, 0,
            cameraX, cameraY, cameraZ, // Camera pos
            0.0f, 0.0f, -5.0f,          // Look at center
            0.0f, 1.0f, 0.0f           // Up direction
        )

        drawGrid(timeSeconds)
        drawFloatingCyberNodes(timeSeconds)
        drawDigitalRainOverlay(timeSeconds)
    }

    private fun drawGrid(timeSeconds: Float) {
        GLES30.glUseProgram(gridShaderProgram)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0.0f, -1.5f, 0.0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES30.glUniformMatrix4fv(gridMvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(gridTimeHandle, timeSeconds)

        GLES30.glBindVertexArray(vao[0])
        GLES30.glLineWidth(2.5f)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVertexCount)
        GLES30.glBindVertexArray(0)
    }

    private fun drawFloatingCyberNodes(timeSeconds: Float) {
        GLES30.glUseProgram(cubeShaderProgram)
        GLES30.glBindVertexArray(vao[1])

        // Render multiple orbiting cyberpunk matrix data nodes
        val nodeCount = 5
        for (i in 0 until nodeCount) {
            val angle = timeSeconds * 0.8f + (i * Math.PI.toFloat() * 2.0f / nodeCount)
            val radius = 3.2f + (i % 2) * 1.2f
            val posX = Math.cos(angle.toDouble()).toFloat() * radius
            val posY = (Math.sin((timeSeconds + i).toDouble()) * 0.6 + 0.5).toFloat()
            val posZ = Math.sin(angle.toDouble()).toFloat() * radius - 4.0f

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
            Matrix.rotateM(modelMatrix, 0, timeSeconds * 45.0f + i * 30.0f, 0.5f, 1.0f, 0.2f)
            Matrix.scaleM(modelMatrix, 0, 0.6f, 0.6f, 0.6f)

            Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

            // Compute Normal Matrix (Transpose of Inverse Model Matrix)
            val invModel = FloatArray(16)
            Matrix.invertM(invModel, 0, modelMatrix, 0)
            Matrix.transposeM(normalMatrix, 0, invModel, 0)

            GLES30.glUniformMatrix4fv(cubeMvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES30.glUniformMatrix4fv(cubeModelMatrixHandle, 1, false, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(cubeNormalMatrixHandle, 1, false, normalMatrix, 0)
            GLES30.glUniform1f(cubeTimeHandle, timeSeconds)

            // Dynamic cyber neon color based on index
            val r = if (i % 2 == 0) 0.0f else 1.0f
            val g = if (i % 3 == 0) 0.9f else 0.4f
            val b = 0.9f
            GLES30.glUniform4f(cubeColorHandle, r, g, b, 0.85f)

            // Draw using compact GL_UNSIGNED_BYTE indices
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, cubeIndexCount, GLES30.GL_UNSIGNED_BYTE, 0)
        }

        GLES30.glBindVertexArray(0)
    }

    private fun setupGridBuffers() {
        val size = 20.0f
        val step = 1.0f
        val gridLines = ArrayList<Short>()

        var x = -size
        while (x <= size) {
            gridLines.add(x.toInt().toShort()); gridLines.add(0.toShort()); gridLines.add((-size).toInt().toShort())
            gridLines.add(x.toInt().toShort()); gridLines.add(0.toShort()); gridLines.add(size.toInt().toShort())
            x += step
        }

        var z = -size
        while (z <= size) {
            gridLines.add((-size).toInt().toShort()); gridLines.add(0.toShort()); gridLines.add(z.toInt().toShort())
            gridLines.add(size.toInt().toShort()); gridLines.add(0.toShort()); gridLines.add(z.toInt().toShort())
            z += step
        }

        gridVertexCount = gridLines.size / 3
        val shortArray = gridLines.toShortArray()
        val vertexBuffer = ByteBuffer.allocateDirect(shortArray.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        vertexBuffer.put(shortArray)
        vertexBuffer.position(0)

        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)

        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            shortArray.size * 2,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Interleaved Position Attribute: 3 x GL_SHORT (6 bytes per vertex)
        GLES30.glEnableVertexAttribArray(gridPositionHandle)
        GLES30.glVertexAttribPointer(
            gridPositionHandle,
            3,
            GLES30.GL_SHORT,
            false,
            3 * 2,
            0
        )

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun setupCubeBuffers() {
        // Compact Interleaved Vertex Layout:
        // Position (3 x GL_SHORT, normalized = true) + Normal (3 x GL_BYTE, normalized = true) + Padding (3 x Byte)
        // Total Stride = 12 bytes per vertex (50% memory reduction vs 24 floats)
        val posNeg = (-16384).toShort()
        val posPos = 16384.toShort()
        val normNeg = (-127).toByte()
        val normPos = 127.toByte()
        val zeroS = 0.toShort()
        val zeroB = 0.toByte()

        val vertexByteBuffer = ByteBuffer.allocateDirect(24 * 12).order(ByteOrder.nativeOrder())

        fun putVertex(px: Short, py: Short, pz: Short, nx: Byte, ny: Byte, nz: Byte) {
            vertexByteBuffer.putShort(px)
            vertexByteBuffer.putShort(py)
            vertexByteBuffer.putShort(pz)
            vertexByteBuffer.put(nx)
            vertexByteBuffer.put(ny)
            vertexByteBuffer.put(nz)
            vertexByteBuffer.put(zeroB) // pad0
            vertexByteBuffer.put(zeroB) // pad1
            vertexByteBuffer.put(zeroB) // pad2
        }

        // Front Face (+Z)
        putVertex(posNeg, posNeg, posPos, zeroB, zeroB, normPos)
        putVertex(posPos, posNeg, posPos, zeroB, zeroB, normPos)
        putVertex(posPos, posPos, posPos, zeroB, zeroB, normPos)
        putVertex(posNeg, posPos, posPos, zeroB, zeroB, normPos)

        // Back Face (-Z)
        putVertex(posNeg, posNeg, posNeg, zeroB, zeroB, normNeg)
        putVertex(posNeg, posPos, posNeg, zeroB, zeroB, normNeg)
        putVertex(posPos, posPos, posNeg, zeroB, zeroB, normNeg)
        putVertex(posPos, posNeg, posNeg, zeroB, zeroB, normNeg)

        // Top Face (+Y)
        putVertex(posNeg, posPos, posNeg, zeroB, normPos, zeroB)
        putVertex(posNeg, posPos, posPos, zeroB, normPos, zeroB)
        putVertex(posPos, posPos, posPos, zeroB, normPos, zeroB)
        putVertex(posPos, posPos, posNeg, zeroB, normPos, zeroB)

        // Bottom Face (-Y)
        putVertex(posNeg, posNeg, posNeg, zeroB, normNeg, zeroB)
        putVertex(posPos, posNeg, posNeg, zeroB, normNeg, zeroB)
        putVertex(posPos, posNeg, posPos, zeroB, normNeg, zeroB)
        putVertex(posNeg, posNeg, posPos, zeroB, normNeg, zeroB)

        // Right Face (+X)
        putVertex(posPos, posNeg, posNeg, normPos, zeroB, zeroB)
        putVertex(posPos, posPos, posNeg, normPos, zeroB, zeroB)
        putVertex(posPos, posPos, posPos, normPos, zeroB, zeroB)
        putVertex(posPos, posNeg, posPos, normPos, zeroB, zeroB)

        // Left Face (-X)
        putVertex(posNeg, posNeg, posNeg, normNeg, zeroB, zeroB)
        putVertex(posNeg, posNeg, posPos, normNeg, zeroB, zeroB)
        putVertex(posNeg, posPos, posPos, normNeg, zeroB, zeroB)
        putVertex(posNeg, posPos, posNeg, normNeg, zeroB, zeroB)

        vertexByteBuffer.position(0)

        // Compact indices: GL_UNSIGNED_BYTE (36 bytes total)
        val cubeIndices = byteArrayOf(
            0, 1, 2, 0, 2, 3,
            4, 5, 6, 4, 6, 7,
            8, 9, 10, 8, 10, 11,
            12, 13, 14, 12, 14, 15,
            16, 17, 18, 16, 18, 19,
            20, 21, 22, 20, 22, 23
        )
        cubeIndexCount = cubeIndices.size

        val indexByteBuffer = ByteBuffer.allocateDirect(cubeIndices.size)
            .order(ByteOrder.nativeOrder())
            .put(cubeIndices)
        indexByteBuffer.position(0)

        GLES30.glGenVertexArrays(1, vao, 1)
        GLES30.glGenBuffers(1, vbo, 1)
        GLES30.glGenBuffers(1, ebo, 0)

        GLES30.glBindVertexArray(vao[1])

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[1])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            24 * 12,
            vertexByteBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            cubeIndices.size,
            indexByteBuffer,
            GLES30.GL_STATIC_DRAW
        )

        val stride = 12 // 12 bytes interleaved stride

        // Interleaved Position Attribute (3 x GL_SHORT, normalized = true, offset = 0)
        GLES30.glEnableVertexAttribArray(cubePositionHandle)
        GLES30.glVertexAttribPointer(
            cubePositionHandle,
            3,
            GLES30.GL_SHORT,
            true,
            stride,
            0
        )

        // Interleaved Normal Attribute (3 x GL_BYTE, normalized = true, offset = 6)
        GLES30.glEnableVertexAttribArray(cubeNormalHandle)
        GLES30.glVertexAttribPointer(
            cubeNormalHandle,
            3,
            GLES30.GL_BYTE,
            true,
            stride,
            6
        )

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun setupOverlayBuffers() {
        // Compact Interleaved Fullscreen Quad:
        // Position (2 x GL_BYTE, normalized = true) + TexCoord (2 x GL_UNSIGNED_BYTE, normalized = true)
        // Stride = 4 bytes per vertex (75% memory reduction)
        val bNeg = (-127).toByte()
        val bPos = 127.toByte()
        val uZero = 0.toByte()
        val uOne = 255.toByte()

        val overlayVertices = byteArrayOf(
            // PosX, PosY, TexU, TexV
            bNeg, bNeg, uZero, uZero,
            bPos, bNeg, uOne,  uZero,
            bPos, bPos, uOne,  uOne,
            bNeg, bNeg, uZero, uZero,
            bPos, bPos, uOne,  uOne,
            bNeg, bPos, uZero, uOne
        )

        val vertexBuffer: ByteBuffer = ByteBuffer.allocateDirect(overlayVertices.size)
            .order(ByteOrder.nativeOrder())
            .put(overlayVertices)
        vertexBuffer.position(0)

        GLES30.glGenVertexArrays(1, vao, 2)
        GLES30.glGenBuffers(1, vbo, 2)

        GLES30.glBindVertexArray(vao[2])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[2])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            overlayVertices.size,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        val stride = 4 // 4 bytes interleaved stride

        // Interleaved Position Attribute (2 x GL_BYTE, normalized = true, offset = 0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_BYTE, true, stride, 0)

        // Interleaved TexCoord Attribute (2 x GL_UNSIGNED_BYTE, normalized = true, offset = 2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_UNSIGNED_BYTE, true, stride, 2)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawDigitalRainOverlay(timeSeconds: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST) // Render overlay on top
        GLES30.glUseProgram(overlayShaderProgram)

        GLES30.glUniform1f(overlayTimeHandle, timeSeconds)
        GLES30.glUniform2f(overlayResolutionHandle, screenWidth, screenHeight)
        // Cyber Green / Emerald Digital Rain Tint
        GLES30.glUniform4f(overlayRainColorHandle, 0.0f, 1.0f, 0.65f, 1.0f)

        GLES30.glBindVertexArray(vao[2])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glBindVertexArray(0)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST) // Restore depth test
    }

    private fun initShaders() {
        // --- 1. Grid Shaders (#version 300 es) ---
        val gridVertexCode = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            uniform mat4 uMVPMatrix;
            uniform float uTime;
            out vec3 vWorldPos;
            
            void main() {
                vWorldPos = aPosition;
                // Subtle dynamic wave motion on matrix grid
                vec3 pos = aPosition;
                pos.y += sin(pos.x * 0.5 + uTime * 2.0) * 0.15;
                gl_Position = uMVPMatrix * vec4(pos, 1.0);
            }
        """.trimIndent()

        val gridFragmentCode = """
            #version 300 es
            precision mediump float;
            in vec3 vWorldPos;
            uniform float uTime;
            out vec4 fragColor;
            
            void main() {
                // Distance fog
                float dist = length(vWorldPos.xz);
                float fog = clamp(1.0 - dist / 18.0, 0.0, 1.0);
                
                // Pulsing cyber cyan/purple grid line color
                float pulse = 0.7 + 0.3 * sin(uTime * 3.0 + vWorldPos.z * 0.5);
                vec3 cyan = vec3(0.0, 0.9, 1.0) * pulse;
                
                fragColor = vec4(cyan * fog, fog * 0.8);
            }
        """.trimIndent()

        gridShaderProgram = createProgram(gridVertexCode, gridFragmentCode)
        gridMvpMatrixHandle = GLES30.glGetUniformLocation(gridShaderProgram, "uMVPMatrix")
        gridTimeHandle = GLES30.glGetUniformLocation(gridShaderProgram, "uTime")
        gridPositionHandle = 0 // matches layout(location = 0)

        // --- 2. Data Cube Shaders (#version 300 es) ---
        val cubeVertexCode = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;
            
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            uniform mat4 uNormalMatrix;
            
            out vec3 vNormal;
            out vec3 vFragPos;
            
            void main() {
                vFragPos = vec3(uModelMatrix * vec4(aPosition, 1.0));
                vNormal = normalize(vec3(uNormalMatrix * vec4(aNormal, 0.0)));
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val cubeFragmentCode = """
            #version 300 es
            precision mediump float;
            
            in vec3 vNormal;
            in vec3 vFragPos;
            
            uniform float uTime;
            uniform vec4 uColor;
            
            out vec4 fragColor;
            
            void main() {
                // Ambient lighting
                vec3 ambient = 0.35 * uColor.rgb;
                
                // Cyber directional light
                vec3 lightDir = normalize(vec3(1.0, 2.0, 1.5));
                float diff = max(dot(vNormal, lightDir), 0.0);
                vec3 diffuse = diff * uColor.rgb * 0.8;
                
                // Holographic edge rim lighting
                vec3 viewDir = normalize(-vFragPos);
                float rim = 1.0 - max(dot(viewDir, vNormal), 0.0);
                rim = pow(rim, 2.5);
                vec3 rimColor = vec3(0.0, 1.0, 0.8) * rim * 1.5;
                
                vec3 finalColor = ambient + diffuse + rimColor;
                fragColor = vec4(finalColor, uColor.a);
            }
        """.trimIndent()

        cubeShaderProgram = createProgram(cubeVertexCode, cubeFragmentCode)
        cubeMvpMatrixHandle = GLES30.glGetUniformLocation(cubeShaderProgram, "uMVPMatrix")
        cubeModelMatrixHandle = GLES30.glGetUniformLocation(cubeShaderProgram, "uModelMatrix")
        cubeNormalMatrixHandle = GLES30.glGetUniformLocation(cubeShaderProgram, "uNormalMatrix")
        cubeTimeHandle = GLES30.glGetUniformLocation(cubeShaderProgram, "uTime")
        cubeColorHandle = GLES30.glGetUniformLocation(cubeShaderProgram, "uColor")
        cubePositionHandle = 0 // matches layout(location = 0)
        cubeNormalHandle = 1   // matches layout(location = 1)

        // --- 3. Digital Rain & CRT Scanline Overlay Shaders (#version 300 es) ---
        val overlayVertexCode = """
            #version 300 es
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            
            out vec2 vTexCoord;
            
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        val overlayFragmentCode = """
            #version 300 es
            precision mediump float;
            
            in vec2 vTexCoord;
            
            uniform float uTime;
            uniform vec2 uResolution;
            uniform vec4 uRainColor;
            
            out vec4 fragColor;
            
            float hash(float n) {
                return fract(sin(n) * 43758.5453123);
            }
            
            float digitalRain(vec2 uv, float time) {
                float columns = 45.0;
                float colId = floor(uv.x * columns);
                float speed = 0.6 + 1.4 * hash(colId * 17.13);
                float offset = hash(colId * 91.41) * 10.0;
                
                float yPos = fract(uv.y * 1.6 - time * speed * 0.35 + offset);
                float head = smoothstep(0.95, 1.0, yPos);
                float tail = pow(yPos, 4.0) * 0.65;
                
                float charNoise = step(0.45, hash(floor(uv.y * 55.0) + floor(time * 10.0) + colId * 33.0));
                return (head * 1.7 + tail) * charNoise;
            }
            
            void main() {
                vec2 uv = vTexCoord;
                float rain = digitalRain(uv, uTime);
                
                // CRT Scanlines
                float scanline = 0.88 + 0.12 * sin(uv.y * uResolution.y * 1.4 + uTime * 6.0);
                
                // Screen Edge Vignette
                vec2 uvCenter = uv * (1.0 - uv.yx);
                float vig = uvCenter.x * uvCenter.y * 15.0;
                vig = clamp(pow(vig, 0.22), 0.0, 1.0);
                
                vec3 rainRGB = uRainColor.rgb * rain;
                vec3 scanlineGlow = vec3(0.0, 0.12, 0.08) * (1.0 - scanline);
                
                vec3 finalColor = (rainRGB + scanlineGlow) * vig;
                float alpha = clamp(rain * 0.6 + (1.0 - scanline) * 0.1, 0.0, 0.8);
                
                fragColor = vec4(finalColor, alpha);
            }
        """.trimIndent()

        overlayShaderProgram = createProgram(overlayVertexCode, overlayFragmentCode)
        overlayTimeHandle = GLES30.glGetUniformLocation(overlayShaderProgram, "uTime")
        overlayResolutionHandle = GLES30.glGetUniformLocation(overlayShaderProgram, "uResolution")
        overlayRainColorHandle = GLES30.glGetUniformLocation(overlayShaderProgram, "uRainColor")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val infoLog = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Error compiling OpenGL ES 3.0 shader: $infoLog")
        }
        return shader
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentCode)

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val infoLog = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Error linking OpenGL ES 3.0 program: $infoLog")
        }
        return program
    }
}
