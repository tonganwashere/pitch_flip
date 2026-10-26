package com.example.pitch_flip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File
import java.io.IOException
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PitchFlip"
        private const val PREFS_NAME = "pitch_flip_prefs"
        private const val MAX_IMAGE_SIZE = 512
    }

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: CoinRenderer
    private lateinit var settingsPanel: ScrollView
    private lateinit var colorGrid: LinearLayout
    private lateinit var skinHeadsText: LinearLayout
    private lateinit var skinHeadsClassicSilver: LinearLayout
    private lateinit var skinHeadsClassicGold: LinearLayout
    private lateinit var skinTailsText: LinearLayout
    private lateinit var skinTailsClassicSilver: LinearLayout
    private lateinit var skinTailsClassicGold: LinearLayout
    private lateinit var headsTextLayout: LinearLayout
    private lateinit var tailsTextLayout: LinearLayout
    private lateinit var skinHeadsDog: LinearLayout
    private lateinit var skinHeadsCat: LinearLayout
    private lateinit var skinHeadsChicken: LinearLayout
    private lateinit var skinHeadsDuck: LinearLayout
    private lateinit var skinHeadsBear: LinearLayout
    private lateinit var skinHeadsMonkey: LinearLayout
    private lateinit var skinTailsDog: LinearLayout
    private lateinit var skinTailsCat: LinearLayout
    private lateinit var skinTailsChicken: LinearLayout
    private lateinit var skinTailsDuck: LinearLayout
    private lateinit var skinTailsBear: LinearLayout
    private lateinit var skinTailsMonkey: LinearLayout
    private lateinit var colorRow1: LinearLayout
    private lateinit var colorRow2: LinearLayout
    private lateinit var colorRow3: LinearLayout
    private lateinit var colorRow4: LinearLayout
    private lateinit var skinHeadsImage: LinearLayout
    private lateinit var skinTailsImage: LinearLayout
    private lateinit var headsImageControls: LinearLayout
    private lateinit var tailsImageControls: LinearLayout
    private lateinit var skinHeadsImageThumb: ImageView
    private lateinit var skinTailsImageThumb: ImageView
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    private lateinit var btnGold: View
    private lateinit var btnSilver: View
    private lateinit var defaultMarker: View
    private var selectedColorBtn: View? = null
    private lateinit var headsInput: EditText
    private lateinit var tailsInput: EditText
    private lateinit var settingsButton: ImageButton
    private lateinit var fallSpeedSeekBar: SeekBar
    private lateinit var rigSwitch: SwitchCompat
    private var currentHeadsSkin = "text"
    private var currentTailsSkin = "text"
    private var pendingImageFace = "heads"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) handleImagePicked(uri)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = CoinRenderer(this)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        glSurfaceView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
                if (!renderer.isSpinning() &&
                    settingsPanel.visibility != View.VISIBLE &&
                    isTouchOnCoin(event.x, event.y)
                ) {
                    val landHeads = getVolumeResult()
                    glSurfaceView.queueEvent { renderer.flip(landHeads) }
                }
            }
            true
        }

        settingsPanel = findViewById(R.id.settingsPanel)
        btnGold = findViewById(R.id.btnGold)
        btnSilver = findViewById(R.id.btnSilver)
        headsInput = findViewById(R.id.headsInput)
        tailsInput = findViewById(R.id.tailsInput)
        rigSwitch = findViewById(R.id.rigSwitch)

        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            val showing = settingsPanel.visibility == View.VISIBLE
            if (showing) {
                settingsPanel.visibility = View.GONE
                colorGrid.visibility = View.GONE
            } else {
                settingsPanel.visibility = View.VISIBLE
                fallSpeedSeekBar.post {
                    val paddingLeft = fallSpeedSeekBar.paddingLeft
                    val paddingRight = fallSpeedSeekBar.paddingRight
                    val trackWidth = fallSpeedSeekBar.width - paddingLeft - paddingRight
                    val ratio = 30f / fallSpeedSeekBar.max
                    val markerX = paddingLeft + ratio * trackWidth - defaultMarker.width / 2f
                    defaultMarker.x = markerX
                }
            }
        }

        btnGold.setOnClickListener {
            btnGold.setBackgroundResource(R.drawable.btn_gold_selected)
            btnSilver.setBackgroundResource(R.drawable.btn_silver_normal)
            clearSelectedColorSwatch()
            glSurfaceView.queueEvent { renderer.setGold(true) }
            savePrefs()
        }

        btnSilver.setOnClickListener {
            btnSilver.setBackgroundResource(R.drawable.btn_silver_selected)
            btnGold.setBackgroundResource(R.drawable.btn_gold_normal)
            clearSelectedColorSwatch()
            glSurfaceView.queueEvent { renderer.setGold(false) }
            savePrefs()
        }

        val btnRainbow = findViewById<View>(R.id.btnRainbow)
        colorGrid = findViewById(R.id.colorGrid)
        colorRow1 = findViewById(R.id.colorRow1)
        colorRow2 = findViewById(R.id.colorRow2)
        colorRow3 = findViewById(R.id.colorRow3)
        colorRow4 = findViewById(R.id.colorRow4)

        btnRainbow.setOnClickListener {
            val showing = colorGrid.visibility == View.VISIBLE
            colorGrid.visibility = if (showing) View.GONE else View.VISIBLE
        }

        buildColorGrid()

        fallSpeedSeekBar = findViewById(R.id.fallSpeedSeekBar)
        defaultMarker = findViewById(R.id.defaultSpeedMarker)

        fallSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val speed = 0.2f + progress * 0.01f
                glSurfaceView.queueEvent { renderer.setAnimationSpeed(speed) }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                savePrefs()
            }
        })

        skinHeadsImage = findViewById(R.id.skinHeadsImage)
        skinTailsImage = findViewById(R.id.skinTailsImage)
        skinHeadsImageThumb = findViewById(R.id.skinHeadsImageThumb)
        skinTailsImageThumb = findViewById(R.id.skinTailsImageThumb)
        headsImageControls = findViewById(R.id.headsImageControls)
        tailsImageControls = findViewById(R.id.tailsImageControls)
        skinHeadsText = findViewById(R.id.skinHeadsText)
        skinHeadsClassicSilver = findViewById(R.id.skinHeadsClassicSilver)
        skinHeadsClassicGold = findViewById(R.id.skinHeadsClassicGold)
        headsTextLayout = findViewById(R.id.headsTextLayout)
        skinHeadsDog = findViewById(R.id.skinHeadsDog)
        skinHeadsCat = findViewById(R.id.skinHeadsCat)
        skinHeadsChicken = findViewById(R.id.skinHeadsChicken)
        skinHeadsDuck = findViewById(R.id.skinHeadsDuck)
        skinHeadsBear = findViewById(R.id.skinHeadsBear)
        skinHeadsMonkey = findViewById(R.id.skinHeadsMonkey)
        tailsTextLayout = findViewById(R.id.tailsTextLayout)
        skinTailsText = findViewById(R.id.skinTailsText)
        skinTailsClassicSilver = findViewById(R.id.skinTailsClassicSilver)
        skinTailsClassicGold = findViewById(R.id.skinTailsClassicGold)
        skinTailsDog = findViewById(R.id.skinTailsDog)
        skinTailsCat = findViewById(R.id.skinTailsCat)
        skinTailsChicken = findViewById(R.id.skinTailsChicken)
        skinTailsDuck = findViewById(R.id.skinTailsDuck)
        skinTailsBear = findViewById(R.id.skinTailsBear)
        skinTailsMonkey = findViewById(R.id.skinTailsMonkey)

        skinHeadsImage.setOnClickListener {
            val existing = loadCustomImageFromDisk("heads")
            if (existing != null) {
                applyCustomImage("heads", existing)
            } else {
                pendingImageFace = "heads"
                imagePickerLauncher.launch("image/*")
            }
        }

        skinTailsImage.setOnClickListener {
            val existing = loadCustomImageFromDisk("tails")
            if (existing != null) {
                applyCustomImage("tails", existing)
            } else {
                pendingImageFace = "tails"
                imagePickerLauncher.launch("image/*")
            }
        }

        val headsImageChange = findViewById<Button>(R.id.headsImageChange)
        val headsImageClear = findViewById<Button>(R.id.headsImageClear)
        val tailsImageChange = findViewById<Button>(R.id.tailsImageChange)
        val tailsImageClear = findViewById<Button>(R.id.tailsImageClear)

        headsImageChange.setOnClickListener {
            pendingImageFace = "heads"
            imagePickerLauncher.launch("image/*")
        }

        headsImageClear.setOnClickListener {
            deleteCustomImage("heads")
            skinHeadsImageThumb.setImageResource(android.R.drawable.ic_input_add)
            headsImageControls.visibility = View.GONE
            currentHeadsSkin = "text"
            setHeadsSkinSelection(skinHeadsText)
            headsTextLayout.visibility = View.VISIBLE
            glSurfaceView.queueEvent { renderer.clearHeadsSkin() }
            savePrefs()
        }

        tailsImageChange.setOnClickListener {
            pendingImageFace = "tails"
            imagePickerLauncher.launch("image/*")
        }

        tailsImageClear.setOnClickListener {
            deleteCustomImage("tails")
            skinTailsImageThumb.setImageResource(android.R.drawable.ic_input_add)
            tailsImageControls.visibility = View.GONE
            currentTailsSkin = "text"
            setTailsSkinSelection(skinTailsText)
            tailsTextLayout.visibility = View.VISIBLE
            glSurfaceView.queueEvent { renderer.clearTailsSkin() }
            savePrefs()
        }

        skinHeadsText.setOnClickListener {
            currentHeadsSkin = "text"
            setHeadsSkinSelection(skinHeadsText)
            headsTextLayout.visibility = View.VISIBLE
            glSurfaceView.queueEvent { renderer.clearHeadsSkin() }
            savePrefs()
        }

        skinHeadsClassicSilver.setOnClickListener {
            currentHeadsSkin = "silver"
            setHeadsSkinSelection(skinHeadsClassicSilver)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_classic_heads) }
            savePrefs()
        }

        skinHeadsClassicGold.setOnClickListener {
            currentHeadsSkin = "gold"
            setHeadsSkinSelection(skinHeadsClassicGold)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_classic_heads_gold) }
            savePrefs()
        }

        skinTailsText.setOnClickListener {
            currentTailsSkin = "text"
            setTailsSkinSelection(skinTailsText)
            tailsTextLayout.visibility = View.VISIBLE
            glSurfaceView.queueEvent { renderer.clearTailsSkin() }
            savePrefs()
        }

        skinTailsClassicSilver.setOnClickListener {
            currentTailsSkin = "silver"
            setTailsSkinSelection(skinTailsClassicSilver)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_classic_tails) }
            savePrefs()
        }

        skinTailsClassicGold.setOnClickListener {
            currentTailsSkin = "gold"
            setTailsSkinSelection(skinTailsClassicGold)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_classic_tails_gold) }
            savePrefs()
        }

        skinHeadsDog.setOnClickListener {
            currentHeadsSkin = "dog"
            setHeadsSkinSelection(skinHeadsDog)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_dog) }
            savePrefs()
        }

        skinHeadsCat.setOnClickListener {
            currentHeadsSkin = "cat"
            setHeadsSkinSelection(skinHeadsCat)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_cat) }
            savePrefs()
        }

        skinHeadsChicken.setOnClickListener {
            currentHeadsSkin = "chicken"
            setHeadsSkinSelection(skinHeadsChicken)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_chicken) }
            savePrefs()
        }

        skinHeadsDuck.setOnClickListener {
            currentHeadsSkin = "duck"
            setHeadsSkinSelection(skinHeadsDuck)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_duck) }
            savePrefs()
        }

        skinHeadsBear.setOnClickListener {
            currentHeadsSkin = "bear"
            setHeadsSkinSelection(skinHeadsBear)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_bear) }
            savePrefs()
        }

        skinHeadsMonkey.setOnClickListener {
            currentHeadsSkin = "monkey"
            setHeadsSkinSelection(skinHeadsMonkey)
            headsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_monkey) }
            savePrefs()
        }

        skinTailsDog.setOnClickListener {
            currentTailsSkin = "dog"
            setTailsSkinSelection(skinTailsDog)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_dog) }
            savePrefs()
        }

        skinTailsCat.setOnClickListener {
            currentTailsSkin = "cat"
            setTailsSkinSelection(skinTailsCat)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_cat) }
            savePrefs()
        }

        skinTailsChicken.setOnClickListener {
            currentTailsSkin = "chicken"
            setTailsSkinSelection(skinTailsChicken)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_chicken) }
            savePrefs()
        }

        skinTailsDuck.setOnClickListener {
            currentTailsSkin = "duck"
            setTailsSkinSelection(skinTailsDuck)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_duck) }
            savePrefs()
        }

        skinTailsBear.setOnClickListener {
            currentTailsSkin = "bear"
            setTailsSkinSelection(skinTailsBear)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_bear) }
            savePrefs()
        }

        skinTailsMonkey.setOnClickListener {
            currentTailsSkin = "monkey"
            setTailsSkinSelection(skinTailsMonkey)
            tailsTextLayout.visibility = View.GONE
            glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_monkey) }
            savePrefs()
        }

        rigSwitch.setOnCheckedChangeListener { _, _ -> savePrefs() }

        val watcher = object : TextWatcher {
            private val handler = Handler(Looper.getMainLooper())
            private var runnable: Runnable? = null

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                runnable?.let { handler.removeCallbacks(it) }
                val task = Runnable {
                    val heads = headsInput.text.toString().trim()
                    val tails = tailsInput.text.toString().trim()
                    glSurfaceView.queueEvent { renderer.setTexts(heads, tails) }
                    savePrefs()
                }
                runnable = task
                handler.postDelayed(task, 500)
            }

            override fun afterTextChanged(s: Editable?) {}
        }
        headsInput.addTextChangedListener(watcher)
        tailsInput.addTextChangedListener(watcher)
        loadPrefs()
    }

    private fun buildColorGrid() {
        val rows = arrayOf(
            intArrayOf(
                0xFF800000.toInt(),
                0xFFDC143C.toInt(),
                0xFFFFA07A.toInt(),
                0xFFE91E63.toInt(),
                0xFFFF1493.toInt(),
                0xFFFF00FF.toInt(),
                0xFF9C27B0.toInt()
            ),
            intArrayOf(
                0xFF8A2BE2.toInt(),
                0xFF673AB7.toInt(),
                0xFF3F51B5.toInt(),
                0xFF191970.toInt(),
                0xFF2196F3.toInt(),
                0xFF00BCD4.toInt(),
                0xFF7FFFD4.toInt()
            ),
            intArrayOf(
                0xFF009688.toInt(),
                0xFF00FF7F.toInt(),
                0xFF228B22.toInt(),
                0xFF00FF00.toInt(),
                0xFF8BC34A.toInt(),
                0xFFCDDC39.toInt(),
                0xFF808000.toInt()
            ),
            intArrayOf(
                0xFFFFEB3B.toInt(),
                0xFFFF9800.toInt(),
                0xFFFF5722.toInt(),
                0xFFD2691E.toInt(),
                0xFFB87333.toInt(),
                0xFF795548.toInt(),
                0xFFFFFDD0.toInt()
            )
        )

        val dp40 = (40 * resources.displayMetrics.density).toInt()
        val dp6 = (6 * resources.displayMetrics.density).toInt()

        val rowLayouts = arrayOf(colorRow1, colorRow2, colorRow3, colorRow4)

        for (r in rows.indices) {
            rowLayouts[r].removeAllViews()
            for (color in rows[r]) {
                val swatch = View(this)
                val params = LinearLayout.LayoutParams(dp40, dp40).apply {
                    marginEnd = dp6
                    bottomMargin = dp6
                }
                swatch.layoutParams = params

                val circle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                swatch.background = circle

                val finalColor = color
                swatch.tag = finalColor
                swatch.setOnClickListener { v ->
                    btnGold.setBackgroundResource(R.drawable.btn_gold_normal)
                    btnSilver.setBackgroundResource(R.drawable.btn_silver_normal)

                    selectedColorBtn?.let { prevBtn ->
                        val prev = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(prevBtn.tag as Int)
                        }
                        prevBtn.background = prev
                    }

                    val selected = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(finalColor)
                        setStroke((3 * resources.displayMetrics.density).toInt(), Color.WHITE)
                    }
                    v.background = selected
                    selectedColorBtn = v

                    glSurfaceView.queueEvent { renderer.setCustomColor(finalColor) }
                    savePrefs()
                }

                rowLayouts[r].addView(swatch)
            }
        }
    }

    private fun isTouchOnCoin(touchX: Float, touchY: Float): Boolean {
        val screenWidth = glSurfaceView.width
        val screenHeight = glSurfaceView.height
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val coinRadiusPx = 0.16f * screenHeight
        val dx = touchX - centerX
        val dy = touchY - centerY
        return (dx * dx + dy * dy) <= (coinRadiusPx * coinRadiusPx)
    }

    private fun savePrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("isGold", renderer.isGold)
            putInt("customColor", renderer.customBgColor)
            putBoolean("isCustomColor", renderer.isCustomColor)
            putString("headsText", headsInput.text.toString())
            putString("tailsText", tailsInput.text.toString())
            putInt("fallSpeed", fallSpeedSeekBar.progress)
            putString("headsSkin", currentHeadsSkin)
            putString("tailsSkin", currentTailsSkin)
            putBoolean("rigMode", rigSwitch.isChecked)
            apply()
        }
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val isGold = prefs.getBoolean("isGold", false)
        val isCustomColor = prefs.getBoolean("isCustomColor", false)
        val customColor = prefs.getInt("customColor", 0)
        rigSwitch.isChecked = prefs.getBoolean("rigMode", true)

        if (isCustomColor) {
            btnGold.setBackgroundResource(R.drawable.btn_gold_normal)
            btnSilver.setBackgroundResource(R.drawable.btn_silver_normal)
            glSurfaceView.queueEvent { renderer.setCustomColor(customColor) }
        } else if (isGold) {
            btnGold.setBackgroundResource(R.drawable.btn_gold_selected)
            btnSilver.setBackgroundResource(R.drawable.btn_silver_normal)
            glSurfaceView.queueEvent { renderer.setGold(true) }
        } else {
            btnSilver.setBackgroundResource(R.drawable.btn_silver_selected)
            btnGold.setBackgroundResource(R.drawable.btn_gold_normal)
            glSurfaceView.queueEvent { renderer.setGold(false) }
        }

        val headsText = prefs.getString("headsText", "") ?: ""
        val tailsText = prefs.getString("tailsText", "") ?: ""
        headsInput.setText(headsText)
        tailsInput.setText(tailsText)

        val headsSkin = prefs.getString("headsSkin", "text") ?: "text"
        val tailsSkin = prefs.getString("tailsSkin", "text") ?: "text"

        when (headsSkin) {
            "image" -> {
                val headsBitmap = loadCustomImageFromDisk("heads")
                if (headsBitmap != null) {
                    currentHeadsSkin = "image"
                    setHeadsSkinSelection(skinHeadsImage)
                    headsTextLayout.visibility = View.GONE
                    skinHeadsImageThumb.setImageBitmap(headsBitmap)
                    glSurfaceView.queueEvent { renderer.setHeadsSkinFromBitmap(headsBitmap) }
                } else {
                    currentHeadsSkin = "text"
                    setHeadsSkinSelection(skinHeadsText)
                    headsTextLayout.visibility = View.VISIBLE
                }
            }
            "silver" -> {
                currentHeadsSkin = "silver"
                setHeadsSkinSelection(skinHeadsClassicSilver)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_classic_heads) }
            }
            "gold" -> {
                currentHeadsSkin = "gold"
                setHeadsSkinSelection(skinHeadsClassicGold)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_classic_heads_gold) }
            }
            "dog" -> {
                currentHeadsSkin = "dog"
                setHeadsSkinSelection(skinHeadsDog)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_dog) }
            }
            "cat" -> {
                currentHeadsSkin = "cat"
                setHeadsSkinSelection(skinHeadsCat)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_cat) }
            }
            "chicken" -> {
                currentHeadsSkin = "chicken"
                setHeadsSkinSelection(skinHeadsChicken)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_chicken) }
            }
            "duck" -> {
                currentHeadsSkin = "duck"
                setHeadsSkinSelection(skinHeadsDuck)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_duck) }
            }
            "bear" -> {
                currentHeadsSkin = "bear"
                setHeadsSkinSelection(skinHeadsBear)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_bear) }
            }
            "monkey" -> {
                currentHeadsSkin = "monkey"
                setHeadsSkinSelection(skinHeadsMonkey)
                headsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setHeadsSkin(R.drawable.skin_monkey) }
            }
            else -> {
                currentHeadsSkin = "text"
                setHeadsSkinSelection(skinHeadsText)
                headsTextLayout.visibility = View.VISIBLE
            }
        }

        when (tailsSkin) {
            "image" -> {
                val tailsBitmap = loadCustomImageFromDisk("tails")
                if (tailsBitmap != null) {
                    currentTailsSkin = "image"
                    setTailsSkinSelection(skinTailsImage)
                    tailsTextLayout.visibility = View.GONE
                    skinTailsImageThumb.setImageBitmap(tailsBitmap)
                    glSurfaceView.queueEvent { renderer.setTailsSkinFromBitmap(tailsBitmap) }
                } else {
                    currentTailsSkin = "text"
                    setTailsSkinSelection(skinTailsText)
                    tailsTextLayout.visibility = View.VISIBLE
                }
            }
            "silver" -> {
                currentTailsSkin = "silver"
                setTailsSkinSelection(skinTailsClassicSilver)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_classic_tails) }
            }
            "gold" -> {
                currentTailsSkin = "gold"
                setTailsSkinSelection(skinTailsClassicGold)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_classic_tails_gold) }
            }
            "dog" -> {
                currentTailsSkin = "dog"
                setTailsSkinSelection(skinTailsDog)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_dog) }
            }
            "cat" -> {
                currentTailsSkin = "cat"
                setTailsSkinSelection(skinTailsCat)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_cat) }
            }
            "chicken" -> {
                currentTailsSkin = "chicken"
                setTailsSkinSelection(skinTailsChicken)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_chicken) }
            }
            "duck" -> {
                currentTailsSkin = "duck"
                setTailsSkinSelection(skinTailsDuck)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_duck) }
            }
            "bear" -> {
                currentTailsSkin = "bear"
                setTailsSkinSelection(skinTailsBear)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_bear) }
            }
            "monkey" -> {
                currentTailsSkin = "monkey"
                setTailsSkinSelection(skinTailsMonkey)
                tailsTextLayout.visibility = View.GONE
                glSurfaceView.queueEvent { renderer.setTailsSkin(R.drawable.skin_monkey) }
            }
            else -> {
                currentTailsSkin = "text"
                setTailsSkinSelection(skinTailsText)
                tailsTextLayout.visibility = View.VISIBLE
            }
        }

        val fallSpeed = prefs.getInt("fallSpeed", 30)
        fallSpeedSeekBar.progress = fallSpeed
        val speed = 0.2f + fallSpeed * 0.01f
        glSurfaceView.queueEvent { renderer.setAnimationSpeed(speed) }
    }

    private fun setHeadsSkinSelection(selected: LinearLayout) {
        skinHeadsText.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsImage.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsClassicSilver.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsClassicGold.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsDog.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsCat.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsChicken.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsDuck.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsBear.setBackgroundResource(R.drawable.skin_normal)
        skinHeadsMonkey.setBackgroundResource(R.drawable.skin_normal)
        selected.setBackgroundResource(R.drawable.skin_selected)
        headsImageControls.visibility =
            if (selected == skinHeadsImage) View.VISIBLE else View.GONE
    }

    private fun setTailsSkinSelection(selected: LinearLayout) {
        skinTailsText.setBackgroundResource(R.drawable.skin_normal)
        skinTailsImage.setBackgroundResource(R.drawable.skin_normal)
        skinTailsClassicSilver.setBackgroundResource(R.drawable.skin_normal)
        skinTailsClassicGold.setBackgroundResource(R.drawable.skin_normal)
        skinTailsDog.setBackgroundResource(R.drawable.skin_normal)
        skinTailsCat.setBackgroundResource(R.drawable.skin_normal)
        skinTailsChicken.setBackgroundResource(R.drawable.skin_normal)
        skinTailsDuck.setBackgroundResource(R.drawable.skin_normal)
        skinTailsBear.setBackgroundResource(R.drawable.skin_normal)
        skinTailsMonkey.setBackgroundResource(R.drawable.skin_normal)
        selected.setBackgroundResource(R.drawable.skin_selected)
        tailsImageControls.visibility =
            if (selected == skinTailsImage) View.VISIBLE else View.GONE
    }

    private fun clearSelectedColorSwatch() {
        selectedColorBtn?.let { btn ->
            val prev = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(btn.tag as Int)
            }
            btn.background = prev
            selectedColorBtn = null
        }
    }

    private fun customImageFile(face: String): File = File(filesDir, "${face}_custom.jpg")

    private fun handleImagePicked(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return
                val scaled = scaleBitmap(bitmap)
                customImageFile(pendingImageFace).outputStream().use { fos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                applyCustomImage(pendingImageFace, scaled)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Image pick failed", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Image pick permission denied", e)
        }
    }

    private fun scaleBitmap(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= MAX_IMAGE_SIZE && h <= MAX_IMAGE_SIZE) return src
        val scale = minOf(MAX_IMAGE_SIZE.toFloat() / w, MAX_IMAGE_SIZE.toFloat() / h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun applyCustomImage(face: String, bitmap: Bitmap) {
        if (face == "heads") {
            currentHeadsSkin = "image"
            setHeadsSkinSelection(skinHeadsImage)
            headsTextLayout.visibility = View.GONE
            skinHeadsImageThumb.setImageBitmap(bitmap)
            glSurfaceView.queueEvent { renderer.setHeadsSkinFromBitmap(bitmap) }
        } else {
            currentTailsSkin = "image"
            setTailsSkinSelection(skinTailsImage)
            tailsTextLayout.visibility = View.GONE
            skinTailsImageThumb.setImageBitmap(bitmap)
            glSurfaceView.queueEvent { renderer.setTailsSkinFromBitmap(bitmap) }
        }
        savePrefs()
    }

    private fun loadCustomImageFromDisk(face: String): Bitmap? {
        val file = customImageFile(face)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun deleteCustomImage(face: String) {
        val file = customImageFile(face)
        if (file.exists()) file.delete()
    }

    private fun getVolumeResult(): Boolean {
        if (!rigSwitch.isChecked) return Random.nextBoolean()
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = (current / max.toFloat()) * 100
        return percent > 50
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focus = currentFocus
        if (focus != null) imm.hideSoftInputFromWindow(focus.windowToken, 0)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (settingsPanel.visibility == View.VISIBLE) {
                val loc = IntArray(2)
                settingsPanel.getLocationOnScreen(loc)
                val settingsBtnLoc = IntArray(2)
                settingsButton.getLocationOnScreen(settingsBtnLoc)
                val x = event.rawX
                val y = event.rawY
                val outside = x < loc[0] || x > loc[0] + settingsPanel.width ||
                    y < loc[1] || y > loc[1] + settingsPanel.height
                val onSettingsBtn = x >= settingsBtnLoc[0] && x <= settingsBtnLoc[0] + settingsButton.width &&
                    y >= settingsBtnLoc[1] && y <= settingsBtnLoc[1] + settingsButton.height
                if (outside && !onSettingsBtn) {
                    settingsPanel.visibility = View.GONE
                    colorGrid.visibility = View.GONE
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }
}
