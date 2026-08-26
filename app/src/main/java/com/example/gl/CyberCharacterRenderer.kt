package com.example.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3.0 GLSurfaceView.Renderer implementation handling the OpenGL lifecycle
 * and rendering loop for 3D Cyberpunk Character Models.
 *
 * Features:
 * - Complete GLSurfaceView.Renderer lifecycle (onSurfaceCreated, onSurfaceChanged, onDrawFrame)
 * - Interleaved vertex attribute buffers with compact types (GL_SHORT for positions, GL_BYTE for normals)
 * - Articulated character model hierarchy (Head/Visor, Torso, Arms, Legs, Plasma Blade)
 * - Real-time animation loop (idle breathing, arm posture sway, glowing cyber visor pulse)
 * - Custom GLSL ES 3.0 Shaders (#version 300 es) with specular & ambient lighting
 */
class CyberCharacterRenderer : GLSurfaceView.Renderer {

    // MVP Matrix arrays
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    // Shader Program
    private var charShaderProgram: Int = 0

    // Shader Uniform Handles
    private var uMvpMatrixHandle: Int = -1
    private var uModelMatrixHandle: Int = -1
    private var uNormalMatrixHandle: Int = -1
    private var uTimeHandle: Int = -1
    private var uColorHandle: Int = -1
    private var uGlowHandle: Int = -1

    // Shader Attribute Locations
    private var aPositionHandle: Int = 0
    private var aNormalHandle: Int = 1

    // Vertex Buffer Objects and Vertex Array Objects
    private val vao = IntArray(1)
    private val vbo = IntArray(2) // 0: VBO, 1: EBO

    private var cubeIndexCount: Int = 0
    private var screenWidth: Float = 1080.0f
    private var screenHeight: Float = 1920.0f

    private var startTimeMs: Long = SystemClock.uptimeMillis()

    // Character view control variables
    @Volatile
    var characterYaw: Float = 0.0f

    @Volatile
    var characterPitch: Float = 0.0f

    @Volatile
    var activeHueR: Float = 0.0f
    @Volatile
    var activeHueG: Float = 1.0f
    @Volatile
    var activeHueB: Float = 0.85f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Clear screen with deep dark space background
        GLES30.glClearColor(0.01f, 0.02f, 0.05f, 1.0f)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        initShaders()
        setupCharacterMeshBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        screenWidth = width.toFloat()
        screenHeight = height.coerceAtLeast(1).toFloat()

        val aspect = screenWidth / screenHeight
        // Set 3D perspective projection frustum
        Matrix.perspectiveM(projectionMatrix, 0, 45.0f, aspect, 0.1f, 100.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val timeSec = (SystemClock.uptimeMillis() - startTimeMs) / 1000.0f

        // Camera positioning looking at character center
        Matrix.setLookAtM(
            viewMatrix, 0,
            0.0f, 1.2f, 4.5f,  // Eye position
            0.0f, 0.8f, 0.0f,  // Center look-at point
            0.0f, 1.0f, 0.0f   // Up vector
        )

        drawCyberCharacterModel(timeSec)
    }

    /**
     * Renders the hierarchical 3D cyber character assembled from compact interleaved mesh parts.
     */
    private fun drawCyberCharacterModel(timeSec: Float) {
        GLES30.glUseProgram(charShaderProgram)
        GLES30.glBindVertexArray(vao[0])

        GLES30.glUniform1f(uTimeHandle, timeSec)

        // Root Character Transformation (Global Rotation & Breathing Float)
        val breathOffsetY = (Math.sin(timeSec.toDouble() * 2.0) * 0.04).toFloat()
        val rootModel = FloatArray(16)
        Matrix.setIdentityM(rootModel, 0)
        Matrix.translateM(rootModel, 0, 0.0f, breathOffsetY, 0.0f)
        Matrix.rotateM(rootModel, 0, characterPitch, 1.0f, 0.0f, 0.0f)
        Matrix.rotateM(rootModel, 0, characterYaw + (timeSec * 15.0f % 360.0f), 0.0f, 1.0f, 0.0f)

        // 1. Torso (Armor Chestplate)
        renderPart(
            parentModel = rootModel,
            tx = 0.0f, ty = 0.8f, tz = 0.0f,
            sx = 0.5f, sy = 0.65f, sz = 0.35f,
            colorR = 0.15f, colorG = 0.20f, colorB = 0.28f,
            glowIntensity = 0.1f
        )

        // 2. Cyber Visor / Head
        val headSway = (Math.sin(timeSec.toDouble() * 1.5) * 3.0).toFloat()
        val headModel = rootModel.clone()
        Matrix.translateM(headModel, 0, 0.0f, 1.25f, 0.0f)
        Matrix.rotateM(headModel, 0, headSway, 0.0f, 1.0f, 0.0f)
        renderPart(
            parentModel = headModel,
            tx = 0.0f, ty = 0.0f, tz = 0.0f,
            sx = 0.3f, sy = 0.3f, sz = 0.32f,
            colorR = 0.08f, colorG = 0.12f, colorB = 0.18f,
            glowIntensity = 0.0f
        )

        // Glowing Neon Visor Band
        renderPart(
            parentModel = headModel,
            tx = 0.0f, ty = 0.02f, tz = 0.14f,
            sx = 0.31f, sy = 0.08f, sz = 0.08f,
            colorR = activeHueR, colorG = activeHueG, colorB = activeHueB,
            glowIntensity = 0.95f
        )

        // 3. Left Arm
        val lArmAngle = (Math.sin(timeSec.toDouble() * 2.5) * 12.0).toFloat()
        val lArmModel = rootModel.clone()
        Matrix.translateM(lArmModel, 0, -0.38f, 1.05f, 0.0f)
        Matrix.rotateM(lArmModel, 0, lArmAngle, 1.0f, 0.0f, 0.0f)
        renderPart(
            parentModel = lArmModel,
            tx = 0.0f, ty = -0.28f, tz = 0.0f,
            sx = 0.14f, sy = 0.5f, sz = 0.14f,
            colorR = 0.2f, colorG = 0.25f, colorB = 0.32f,
            glowIntensity = 0.2f
        )

        // 4. Right Arm (Holding Plasma Blade)
        val rArmModel = rootModel.clone()
        Matrix.translateM(rArmModel, 0, 0.38f, 1.05f, 0.0f)
        Matrix.rotateM(rArmModel, 0, -25.0f + lArmAngle * 0.5f, 1.0f, 0.0f, 0.0f)
        renderPart(
            parentModel = rArmModel,
            tx = 0.0f, ty = -0.28f, tz = 0.0f,
            sx = 0.14f, sy = 0.5f, sz = 0.14f,
            colorR = 0.2f, colorG = 0.25f, colorB = 0.32f,
            glowIntensity = 0.2f
        )

        // Plasma Cyber Blade (Glowing weapon)
        renderPart(
            parentModel = rArmModel,
            tx = 0.0f, ty = -0.55f, tz = 0.35f,
            sx = 0.04f, sy = 0.06f, sz = 0.7f,
            colorR = activeHueR, colorG = activeHueG, colorB = activeHueB,
            glowIntensity = 1.0f
        )

        // 5. Left Leg
        renderPart(
            parentModel = rootModel,
            tx = -0.16f, ty = 0.22f, tz = 0.0f,
            sx = 0.16f, sy = 0.55f, sz = 0.18f,
            colorR = 0.12f, colorG = 0.16f, colorB = 0.22f,
            glowIntensity = 0.1f
        )

        // 6. Right Leg
        renderPart(
            parentModel = rootModel,
            tx = 0.16f, ty = 0.22f, tz = 0.0f,
            sx = 0.16f, sy = 0.55f, sz = 0.18f,
            colorR = 0.12f, colorG = 0.16f, colorB = 0.22f,
            glowIntensity = 0.1f
        )

        GLES30.glBindVertexArray(0)
    }

    private fun renderPart(
        parentModel: FloatArray,
        tx: Float, ty: Float, tz: Float,
        sx: Float, sy: Float, sz: Float,
        colorR: Float, colorG: Float, colorB: Float,
        glowIntensity: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, tx, ty, tz)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(modelMatrix, 0, parentModel, 0, modelMatrix.clone(), 0)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        val invModel = FloatArray(16)
        Matrix.invertM(invModel, 0, modelMatrix, 0)
        Matrix.transposeM(normalMatrix, 0, invModel, 0)

        GLES30.glUniformMatrix4fv(uMvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uNormalMatrixHandle, 1, false, normalMatrix, 0)
        GLES30.glUniform4f(uColorHandle, colorR, colorG, colorB, 1.0f)
        GLES30.glUniform1f(uGlowHandle, glowIntensity)

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, cubeIndexCount, GLES30.GL_UNSIGNED_BYTE, 0)
    }

    /**
     * Initializes vertex buffers using compact interleaved vertex formatting:
     * Position (3 x GL_SHORT, normalized = true) + Normal (3 x GL_BYTE, normalized = true) + Padding (3 x Byte)
     * Stride = 12 bytes per vertex.
     */
    private fun setupCharacterMeshBuffers() {
        val posNeg = (-16384).toShort()
        val posPos = 16384.toShort()
        val normNeg = (-127).toByte()
        val normPos = 127.toByte()
        val zeroB = 0.toByte()

        val vertexByteBuffer = ByteBuffer.allocateDirect(24 * 12).order(ByteOrder.nativeOrder())

        fun putVertex(px: Short, py: Short, pz: Short, nx: Byte, ny: Byte, nz: Byte) {
            vertexByteBuffer.putShort(px)
            vertexByteBuffer.putShort(py)
            vertexByteBuffer.putShort(pz)
            vertexByteBuffer.put(nx)
            vertexByteBuffer.put(ny)
            vertexByteBuffer.put(nz)
            vertexByteBuffer.put(zeroB)
            vertexByteBuffer.put(zeroB)
            vertexByteBuffer.put(zeroB)
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

        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(2, vbo, 0)

        GLES30.glBindVertexArray(vao[0])

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 24 * 12, vertexByteBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, cubeIndices.size, indexByteBuffer, GLES30.GL_STATIC_DRAW)

        val stride = 12 // 12 bytes interleaved stride

        // Interleaved Position Attribute: 3 x GL_SHORT (normalized = true, offset = 0)
        GLES30.glEnableVertexAttribArray(aPositionHandle)
        GLES30.glVertexAttribPointer(aPositionHandle, 3, GLES30.GL_SHORT, true, stride, 0)

        // Interleaved Normal Attribute: 3 x GL_BYTE (normalized = true, offset = 6)
        GLES30.glEnableVertexAttribArray(aNormalHandle)
        GLES30.glVertexAttribPointer(aNormalHandle, 3, GLES30.GL_BYTE, true, stride, 6)

        GLES30.glBindVertexArray(0)
    }

    private fun initShaders() {
        val vertexShaderSource = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;

            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            uniform mat4 uNormalMatrix;

            out vec3 vNormal;
            out vec3 vWorldPos;

            void main() {
                vWorldPos = vec3(uModelMatrix * vec4(aPosition, 1.0));
                vNormal = normalize(mat3(uNormalMatrix) * aNormal);
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val fragmentShaderSource = """
            #version 300 es
            precision mediump float;

            in vec3 vNormal;
            in vec3 vWorldPos;

            uniform float uTime;
            uniform vec4 uColor;
            uniform float uGlow;

            out vec4 fragColor;

            void main() {
                // Directional Key Light
                vec3 lightDir = normalize(vec3(0.5, 1.2, 1.0));
                float diff = max(dot(vNormal, lightDir), 0.0);

                // Rim Lighting for Cyber Accent
                vec3 viewDir = normalize(vec3(0.0, 1.2, 4.5) - vWorldPos);
                float rim = 1.0 - max(dot(viewDir, vNormal), 0.0);
                rim = pow(rim, 3.0) * 0.7;

                // Glowing Neon Pulse effect
                float pulse = 0.85 + 0.15 * sin(uTime * 4.0);
                vec3 finalColor = uColor.rgb * (diff * 0.7 + 0.35) + vec3(rim) * uColor.rgb;

                if (uGlow > 0.5) {
                    finalColor += uColor.rgb * uGlow * pulse;
                }

                fragColor = vec4(finalColor, uColor.a);
            }
        """.trimIndent()

        val vertShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource)
        val fragShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)

        charShaderProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(charShaderProgram, vertShader)
        GLES30.glAttachShader(charShaderProgram, fragShader)
        GLES30.glLinkProgram(charShaderProgram)

        uMvpMatrixHandle = GLES30.glGetUniformLocation(charShaderProgram, "uMVPMatrix")
        uModelMatrixHandle = GLES30.glGetUniformLocation(charShaderProgram, "uModelMatrix")
        uNormalMatrixHandle = GLES30.glGetUniformLocation(charShaderProgram, "uNormalMatrix")
        uTimeHandle = GLES30.glGetUniformLocation(charShaderProgram, "uTime")
        uColorHandle = GLES30.glGetUniformLocation(charShaderProgram, "uColor")
        uGlowHandle = GLES30.glGetUniformLocation(charShaderProgram, "uGlow")
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            Log.e("CyberCharacterRenderer", "Shader compilation error: $log")
        }
        return shader
    }
}
