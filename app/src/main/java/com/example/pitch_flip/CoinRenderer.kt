package com.example.pitch_flip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.core.content.res.ResourcesCompat
import java.nio.Buffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CoinRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val VERTEX_SHADER = """
uniform mat4 uMVPMatrix;
uniform mat4 uModelMatrix;
attribute vec4 aPosition;
attribute vec3 aNormal;
attribute vec2 aTexCoord;
varying vec3 vNormal;
varying vec3 vWorldPos;
varying vec2 vTexCoord;
void main() {
  gl_Position = uMVPMatrix * aPosition;
  vNormal = normalize(mat3(uModelMatrix) * aNormal);
  vWorldPos = vec3(uModelMatrix * aPosition);
  vTexCoord = aTexCoord;
}
"""

        private const val FRAGMENT_SHADER = """
precision mediump float;
uniform vec3 uLightPos;
uniform vec3 uColor;
uniform vec3 uCameraPos;
uniform sampler2D uTexture;
uniform float uUseTexture;
varying vec3 vNormal;
varying vec3 vWorldPos;
varying vec2 vTexCoord;
void main() {
  float ambient = 0.3;
  vec3 lightDir = normalize(uLightPos - vWorldPos);
  float diffuse = max(dot(vNormal, lightDir), 0.0);
  vec3 viewDir = normalize(uCameraPos - vWorldPos);
  vec3 reflectDir = reflect(-lightDir, vNormal);
  float specular = pow(max(dot(viewDir, reflectDir), 0.0), 32.0) * 0.8;
  vec4 texSample = texture2D(uTexture, vTexCoord);
  vec3 baseColor = mix(uColor, texSample.rgb, uUseTexture);
  vec3 result = (ambient + diffuse + specular) * baseColor;
  gl_FragColor = vec4(result, 1.0);
}
"""

        private const val WINDUP_FRAMES = 20
        private const val FLICK_FRAMES = 8
        private const val AIRBORNE_FRAMES = 70
        private const val REVEAL_IN = 45
        private const val REVEAL_HOLD = 30
        private const val MAX_WINDUP = -60f
        private const val MAX_HEIGHT = 0.55f
        private const val TEXTURE_SIZE = 512
        private const val RIDGE_COUNT = 60

        private val SILVER_FACE = floatArrayOf(0.88f, 0.88f, 0.92f)
        private val SILVER_EDGE = floatArrayOf(0.55f, 0.55f, 0.60f)
        private val GOLD_FACE = floatArrayOf(0.92f, 0.76f, 0.22f)
        private val GOLD_EDGE = floatArrayOf(0.72f, 0.56f, 0.12f)
    }

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var program = 0
    private var aPositionHandle = 0
    private var aNormalHandle = 0
    private var aTexCoordHandle = 0
    private var uMVPMatrixHandle = 0
    private var uModelMatrixHandle = 0
    private var uLightPosHandle = 0
    private var uColorHandle = 0
    private var uCameraPosHandle = 0
    private var uTextureHandle = 0
    private var uUseTextureHandle = 0

    private lateinit var coin: CoinGeometry

    private var hasFlipped = false
    private var headsTexture = 0
    private var tailsTexture = 0
    private var startTexture = 0
    private var ridgeTexture = 0
    private var skinHeadsTexture = 0
    private var skinTailsTexture = 0

    var customBgColor = 0
        private set
    private var customFaceColor: FloatArray? = null
    private var customEdgeColor: FloatArray? = null
    var isCustomColor = false
        private set
    private var animationSpeed = 1.0f
    private var useHeadsSkin = false
    private var useTailsSkin = false
    @Volatile
    private var pendingSkinHeads = -1
    @Volatile
    private var pendingSkinTails = -1
    private var activeHeadsBitmap: Bitmap? = null
    private var activeTailsBitmap: Bitmap? = null

    private fun easeInOut(t: Float): Float =
        if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t

    private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

    private var landHeads = true

    private enum class FlipPhase { IDLE, WINDUP, FLICK, AIRBORNE, REVEAL }

    private var phase = FlipPhase.IDLE
    private var phaseFrame = 0f

    private var presentationTilt = 0f
    private var flipPitch = 0f
    private var coinY = 0f
    private var startFlipPitch = 0f
    private var targetFlipPitch = 0f
    private var airborneTargetPitch = 0f

    var isGold = false
        private set
    private var headsText = "HEADS"
    private var tailsText = "TAILS"
    @Volatile
    private var needsRebuild = false

    fun interface FlipCallback {
        fun onFlipDone(isHeads: Boolean)
    }

    private var flipCallback: FlipCallback? = null

    fun setFlipCallback(cb: FlipCallback) {
        this.flipCallback = cb
    }

    fun isSpinning(): Boolean = phase != FlipPhase.IDLE

    fun setGold(gold: Boolean) {
        isGold = gold
        isCustomColor = false
        needsRebuild = true
    }

    fun setTexts(heads: String, tails: String) {
        headsText = if (heads.isEmpty()) "HEADS" else heads.uppercase()
        tailsText = if (tails.isEmpty()) "TAILS" else tails.uppercase()
        needsRebuild = true
    }

    fun setCustomColor(androidColor: Int) {
        val r = Color.red(androidColor) / 255f
        val g = Color.green(androidColor) / 255f
        val b = Color.blue(androidColor) / 255f
        customFaceColor = floatArrayOf(r, g, b)
        customEdgeColor = floatArrayOf(r * 0.65f, g * 0.65f, b * 0.65f)
        customBgColor = androidColor
        isCustomColor = true
        isGold = false
        needsRebuild = true
    }

    fun setAnimationSpeed(speed: Float) {
        animationSpeed = speed
    }

    fun setHeadsSkin(headsRes: Int) {
        pendingSkinHeads = headsRes
        activeHeadsBitmap = null
        useHeadsSkin = true
        needsRebuild = true
    }

    fun setTailsSkin(tailsRes: Int) {
        pendingSkinTails = tailsRes
        activeTailsBitmap = null
        useTailsSkin = true
        needsRebuild = true
    }

    fun setHeadsSkinFromBitmap(bitmap: Bitmap) {
        activeHeadsBitmap = bitmap
        pendingSkinHeads = -1
        useHeadsSkin = true
        needsRebuild = true
    }

    fun setTailsSkinFromBitmap(bitmap: Bitmap) {
        activeTailsBitmap = bitmap
        pendingSkinTails = -1
        useTailsSkin = true
        needsRebuild = true
    }

    fun clearHeadsSkin() {
        useHeadsSkin = false
        activeHeadsBitmap = null
        needsRebuild = true
    }

    fun clearTailsSkin() {
        useTailsSkin = false
        activeTailsBitmap = null
        needsRebuild = true
    }

    fun flip(landOnHeads: Boolean) {
        if (phase != FlipPhase.IDLE) return
        landHeads = landOnHeads
        val landing = if (landOnHeads) 0f else 180f
        val current = flipPitch % 360f
        targetFlipPitch = flipPitch + 1800f + ((landing - current + 360f) % 360f)
        airborneTargetPitch = targetFlipPitch - 90f
        startFlipPitch = flipPitch
        phase = FlipPhase.WINDUP
        phaseFrame = 0f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        aTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix")
        uLightPosHandle = GLES20.glGetUniformLocation(program, "uLightPos")
        uColorHandle = GLES20.glGetUniformLocation(program, "uColor")
        uCameraPosHandle = GLES20.glGetUniformLocation(program, "uCameraPos")
        uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        uUseTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture")

        coin = CoinGeometry()

        skinHeadsTexture = 0
        skinTailsTexture = 0
        buildTextures()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 10f)
    }

    private fun deleteTextures() {
        GLES20.glDeleteTextures(4, intArrayOf(headsTexture, tailsTexture, ridgeTexture, startTexture), 0)
        if (skinHeadsTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(skinHeadsTexture), 0)
            skinHeadsTexture = 0
        }
        if (skinTailsTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(skinTailsTexture), 0)
            skinTailsTexture = 0
        }
    }

    private fun buildTextures() {
        headsTexture = createTextTexture(headsText, !useHeadsSkin)
        tailsTexture = createTextTexture(tailsText, !useTailsSkin)
        startTexture = createTextTexture("FLIP", true)
        ridgeTexture = createRidgeTexture()
        if (useHeadsSkin) {
            val hBitmap = activeHeadsBitmap
            if (hBitmap != null) {
                skinHeadsTexture = createImageTextureFromBitmap(hBitmap)
            } else if (pendingSkinHeads != -1) {
                skinHeadsTexture = createImageTexture(pendingSkinHeads, 0.85f)
            }
        }
        if (useTailsSkin) {
            val tBitmap = activeTailsBitmap
            if (tBitmap != null) {
                skinTailsTexture = createImageTextureFromBitmap(tBitmap)
            } else if (pendingSkinTails != -1) {
                skinTailsTexture = createImageTexture(pendingSkinTails, 0.75f)
            }
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (needsRebuild) {
            needsRebuild = false
            deleteTextures()
            buildTextures()
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)

        when (phase) {
            FlipPhase.WINDUP -> {
                val p = phaseFrame / WINDUP_FRAMES
                presentationTilt = MAX_WINDUP * easeInOut(p)
                phaseFrame += animationSpeed
                if (phaseFrame >= WINDUP_FRAMES) {
                    presentationTilt = MAX_WINDUP
                    phase = FlipPhase.FLICK
                    phaseFrame = 0f
                }
            }

            FlipPhase.FLICK -> {
                val p = phaseFrame / FLICK_FRAMES
                presentationTilt = MAX_WINDUP * (1f - p)
                phaseFrame += animationSpeed
                if (phaseFrame >= FLICK_FRAMES) {
                    presentationTilt = 0f
                    hasFlipped = true
                    phase = FlipPhase.AIRBORNE
                    phaseFrame = 0f
                }
            }

            FlipPhase.AIRBORNE -> {
                val p = phaseFrame / AIRBORNE_FRAMES
                coinY = MAX_HEIGHT * sin(PI.toFloat() * p)
                val spinT = p * 0.3f + easeOutQuad(p) * 0.9f
                flipPitch = startFlipPitch + spinT * (airborneTargetPitch - startFlipPitch)
                phaseFrame += animationSpeed
                if (phaseFrame >= AIRBORNE_FRAMES) {
                    flipPitch = airborneTargetPitch
                    coinY = 0f
                    phase = FlipPhase.REVEAL
                    phaseFrame = 0f
                }
            }

            FlipPhase.REVEAL -> {
                val revealTotal = REVEAL_IN + REVEAL_HOLD
                if (phaseFrame < REVEAL_IN) {
                    val p = phaseFrame / REVEAL_IN
                    flipPitch = airborneTargetPitch + easeInOut(p) * 90f
                } else {
                    flipPitch = targetFlipPitch
                }
                phaseFrame += animationSpeed
                if (phaseFrame >= revealTotal) {
                    phase = FlipPhase.IDLE
                    flipCallback?.onFlipDone(landHeads)
                }
            }

            else -> {}
        }

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, coinY, 0f)
        Matrix.rotateM(modelMatrix, 0, flipPitch + presentationTilt, 1f, 0f, 0f)

        Matrix.multiplyMM(tempMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniform3f(uLightPosHandle, 2f, 3f, 3f)
        GLES20.glUniform3f(uCameraPosHandle, 0f, 0f, 3f)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)

        drawFaces()
        drawEdge()
    }

    private fun drawFaces() {
        val strideBytes = CoinGeometry.STRIDE_FLOATS * 4
        val verticesPerSide = coin.faceVertexCount / 2
        val defaultFace = if (isGold) GOLD_FACE else SILVER_FACE
        val faceColor = if (isCustomColor) customFaceColor ?: defaultFace else defaultFace

        for (side in 0 until 2) {
            val floatOffset = side * verticesPerSide * CoinGeometry.STRIDE_FLOATS
            val textureId = if (side == 0) {
                if (hasFlipped) {
                    if (useHeadsSkin) skinHeadsTexture else headsTexture
                } else {
                    startTexture
                }
            } else {
                if (useTailsSkin) skinTailsTexture else tailsTexture
            }

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(uTextureHandle, 0)
            GLES20.glUniform1f(uUseTextureHandle, 1.0f)
            GLES20.glUniform3fv(uColorHandle, 1, faceColor, 0)

            (coin.faceVertices as Buffer).position(floatOffset)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, strideBytes, coin.faceVertices)
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            (coin.faceVertices as Buffer).position(floatOffset + 3)
            GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, strideBytes, coin.faceVertices)
            GLES20.glEnableVertexAttribArray(aNormalHandle)

            (coin.faceVertices as Buffer).position(floatOffset + 6)
            GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, strideBytes, coin.faceVertices)
            GLES20.glEnableVertexAttribArray(aTexCoordHandle)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, verticesPerSide)
        }
    }

    private fun drawEdge() {
        val strideBytes = CoinGeometry.STRIDE_FLOATS * 4
        val defaultEdge = if (isGold) GOLD_EDGE else SILVER_EDGE
        val edgeColor = if (isCustomColor) customEdgeColor ?: defaultEdge else defaultEdge

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ridgeTexture)
        GLES20.glUniform1i(uTextureHandle, 0)
        GLES20.glUniform1f(uUseTextureHandle, 1.0f)
        GLES20.glUniform3fv(uColorHandle, 1, edgeColor, 0)

        (coin.edgeVertices as Buffer).position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, strideBytes, coin.edgeVertices)
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        (coin.edgeVertices as Buffer).position(3)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, strideBytes, coin.edgeVertices)
        GLES20.glEnableVertexAttribArray(aNormalHandle)

        (coin.edgeVertices as Buffer).position(6)
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, strideBytes, coin.edgeVertices)
        GLES20.glEnableVertexAttribArray(aTexCoordHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, coin.edgeVertexCount)
    }

    private val coinBgColor: Int
        get() = when {
            isCustomColor -> customBgColor
            isGold -> Color.argb(255, 210, 170, 50)
            else -> Color.argb(255, 200, 200, 212)
        }

    private fun loadTexture(bitmap: Bitmap, wrapS: Int = GLES20.GL_CLAMP_TO_EDGE): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrapS)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        bitmap.recycle()
        return texIds[0]
    }

    private fun drawRidgeTicks(canvas: Canvas, center: Float, radius: Float) {
        val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        val tickOuter = radius - 2
        val tickInner = radius - 12
        for (i in 0 until RIDGE_COUNT) {
            val angle = 2.0 * PI * i / RIDGE_COUNT
            val cosVal = cos(angle).toFloat()
            val sinVal = sin(angle).toFloat()
            ridgePaint.color = if (i % 2 == 0) {
                Color.argb(255, 100, 100, 115)
            } else {
                Color.argb(255, 230, 230, 240)
            }
            canvas.drawLine(
                center + cosVal * tickInner,
                center + sinVal * tickInner,
                center + cosVal * tickOuter,
                center + sinVal * tickOuter,
                ridgePaint
            )
        }
    }

    private fun createTextTexture(text: String, forceText: Boolean): Int {
        val size = TEXTURE_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val outerRadius = size / 2f - 4

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coinBgColor
        }
        canvas.drawCircle(center, center, outerRadius, bgPaint)

        drawRidgeTicks(canvas, center, outerRadius)

        val tickInner = outerRadius - 12
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCustomColor) {
                Color.argb(
                    180,
                    (Color.red(customBgColor) * 0.6f).toInt(),
                    (Color.green(customBgColor) * 0.6f).toInt(),
                    (Color.blue(customBgColor) * 0.6f).toInt()
                )
            } else if (isGold) {
                Color.argb(255, 160, 120, 30)
            } else {
                Color.argb(255, 140, 140, 158)
            }
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(center, center, tickInner - 6, ringPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCustomColor) {
                Color.argb(255, 30, 30, 30)
            } else if (isGold) {
                Color.argb(255, 90, 60, 10)
            } else {
                Color.argb(255, 65, 65, 80)
            }
            textSize = 90f
            textAlign = Paint.Align.CENTER
            typeface = ResourcesCompat.getFont(context, R.font.cinzel)
        }
        val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
        if (forceText) canvas.drawText(text, center, textY, textPaint)

        return loadTexture(bitmap)
    }

    private fun createRidgeTexture(): Int {
        val width = TEXTURE_SIZE
        val height = 64
        val ridgeCount = RIDGE_COUNT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val silverPaint = Paint().apply {
            color = if (isCustomColor) {
                Color.argb(
                    255,
                    (Color.red(customBgColor) * 0.85f).toInt(),
                    (Color.green(customBgColor) * 0.85f).toInt(),
                    (Color.blue(customBgColor) * 0.85f).toInt()
                )
            } else if (isGold) {
                Color.argb(255, 200, 160, 40)
            } else {
                Color.argb(255, 160, 160, 172)
            }
        }
        val darkPaint = Paint().apply {
            color = if (isCustomColor) {
                Color.argb(
                    255,
                    (Color.red(customBgColor) * 0.35f).toInt(),
                    (Color.green(customBgColor) * 0.35f).toInt(),
                    (Color.blue(customBgColor) * 0.35f).toInt()
                )
            } else if (isGold) {
                Color.argb(255, 80, 55, 10)
            } else {
                Color.argb(255, 40, 40, 48)
            }
        }

        val stripeWidth = width.toFloat() / ridgeCount
        for (i in 0 until ridgeCount) {
            val left = i * stripeWidth
            val mid = left + stripeWidth * 0.5f
            val right = left + stripeWidth
            canvas.drawRect(left, 0f, mid, height.toFloat(), silverPaint)
            canvas.drawRect(mid, 0f, right, height.toFloat(), darkPaint)
        }

        return loadTexture(bitmap, GLES20.GL_REPEAT)
    }

    private fun createImageTexture(resourceId: Int, scale: Float): Int {
        val size = TEXTURE_SIZE
        var source = BitmapFactory.decodeResource(context.resources, resourceId)
        if (source == null) {
            source = BitmapFactory.decodeResource(context.resources, R.drawable.skin_classic_heads)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = size / 2f - 4

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coinBgColor
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        canvas.save()
        val clipPath = Path().apply {
            addCircle(center, center, radius - 20, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        val src = Rect(0, 0, source.width, source.height)
        val padding = size * (1f - scale) / 2f
        val dst = RectF(padding, padding, size - padding, size - padding)
        canvas.drawBitmap(source, src, dst, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restore()

        canvas.save()
        val fullClip = Path().apply {
            addCircle(center, center, radius, Path.Direction.CW)
        }
        canvas.clipPath(fullClip)
        drawRidgeTicks(canvas, center, radius)
        canvas.restore()

        source.recycle()
        return loadTexture(bitmap)
    }

    private fun createImageTextureFromBitmap(source: Bitmap): Int {
        val size = TEXTURE_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = size / 2f - 4

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coinBgColor
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        canvas.save()
        val clipPath = Path().apply {
            addCircle(center, center, radius - 4, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        val src = Rect(0, 0, source.width, source.height)
        val dst = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawBitmap(source, src, dst, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restore()

        canvas.save()
        val fullClip = Path().apply {
            addCircle(center, center, radius, Path.Direction.CW)
        }
        canvas.clipPath(fullClip)
        drawRidgeTicks(canvas, center, radius)
        canvas.restore()

        return loadTexture(bitmap)
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        fun compile(type: Int, code: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, code)
            GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vertexCode))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fragmentCode))
        GLES20.glLinkProgram(p)
        return p
    }
}
