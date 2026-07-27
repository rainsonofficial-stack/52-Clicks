package com.chronocard.app

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.CycleInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PasscodeActivity : AppCompatActivity() {

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 10000L
    }

    private val entered = StringBuilder()
    private lateinit var dots: List<View>
    private lateinit var dotsContainer: LinearLayout
    private lateinit var blackOverlay: View
    private val handler = Handler(Looper.getMainLooper())
    private var awake = false

    private val sleepRunnable = Runnable { goToSleep() }

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode)
        goFullscreen()

        val ivBackground = findViewById<ImageView>(R.id.ivBackground)
        SetupPrefs.getBackground(this)?.let { path ->
            ivBackground.setImageURI(Uri.fromFile(File(path)))
        }

        updateClock()
        handler.post(clockRunnable)
        dotsContainer = findViewById(R.id.dotsContainer)
        buildDots()
        buildKeypad()

        blackOverlay = findViewById(R.id.blackOverlay)
        blackOverlay.setOnClickListener { wakeUp() }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (awake && ev.action == android.view.MotionEvent.ACTION_DOWN) {
            resetSleepTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun wakeUp() {
        awake = true
        blackOverlay.visibility = View.GONE
        resetSleepTimer()
    }

    private fun goToSleep() {
        awake = false
        entered.clear()
        refreshDots()
        blackOverlay.visibility = View.VISIBLE
        handler.removeCallbacks(sleepRunnable)
    }

    private fun resetSleepTimer() {
        handler.removeCallbacks(sleepRunnable)
        handler.postDelayed(sleepRunnable, INACTIVITY_TIMEOUT_MS)
    }

    private fun goFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        findViewById<TextView>(R.id.tvClock).text =
            SimpleDateFormat("h:mm", Locale.getDefault()).format(now.time)
        findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now.time)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildDots() {
        val dotList = mutableListOf<View>()
        repeat(4) {
            val dot = View(this).apply {
                val s = dp(12)
                layoutParams = LinearLayout.LayoutParams(s, s).also { p -> p.marginEnd = dp(20) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(2), Color.WHITE)
                    setColor(Color.TRANSPARENT)
                }
            }
            dotsContainer.addView(dot)
            dotList.add(dot)
        }
        dots = dotList
    }

    private fun buildKeypad() {
        val container = findViewById<LinearLayout>(R.id.keypadContainer)
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        rows.forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(14) }
            }
            row.forEach { label ->
                val btnSize = dp(76)
                val margin = dp(12)
                if (label.isEmpty()) {
                    rowLayout.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                            .also { it.marginEnd = margin }
                    })
                } else {
                    rowLayout.addView(TextView(this).apply {
                        text = label
                        textSize = if (label == "⌫") 20f else 26f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                            .also { it.marginEnd = margin }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#40FFFFFF"))
                        }
                        setOnClickListener { onKey(label) }
                    })
                }
            }
            container.addView(rowLayout)
        }
    }

    private fun onKey(label: String) {
        when (label) {
            "⌫" -> if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
            else -> if (entered.length < 4) entered.append(label)
        }
        refreshDots()
        if (entered.length == 4) {
            val card = CardUtils.decodePasscode(entered.toString())
            if (card != null) {
                GalleryInserter.performInsertForCard(this, card)
                finishAffinity()
            } else {
                shakeAndVibrate()
            }
        }
    }

    private fun refreshDots() {
        dots.forEachIndexed { i, dot ->
            (dot.background as GradientDrawable).setColor(
                if (i < entered.length) Color.WHITE else Color.TRANSPARENT
            )
        }
    }

    private fun shakeAndVibrate() {
        vibrate()
        val anim = ObjectAnimator.ofFloat(
            dotsContainer, "translationX", 0f, 22f, -22f, 16f, -16f, 8f, -8f, 0f
        )
        anim.duration = 400
        anim.interpolator = CycleInterpolator(1f)
        anim.start()
        dotsContainer.postDelayed({
            entered.clear()
            refreshDots()
        }, 380)
    }

    private fun vibrate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
                .vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(300)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
