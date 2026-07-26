package com.chronocard.app

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.animation.CycleInterpolator
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PasscodeActivity : AppCompatActivity() {

    private val entered = StringBuilder()
    private lateinit var dots: List<View>
    private lateinit var dotsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode)

        dotsContainer = findViewById(R.id.dotsContainer)
        buildDots()
        buildKeypad()
    }

    private fun buildDots() {
        val dotList = mutableListOf<View>()
        repeat(4) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(14, 14).also { it.marginEnd = 22 }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(2, android.graphics.Color.WHITE)
                }
            }
            dotsContainer.addView(dot)
            dotList.add(dot)
        }
        dots = dotList
    }

    private fun buildKeypad() {
        val grid = findViewById<GridLayout>(R.id.keypad)
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        labels.forEach { label ->
            val tv = TextView(this).apply {
                text = label
                textSize = 26f
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
                val size = 76
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size; height = size
                    setMargins(10, 10, 10, 10)
                }
                if (label.isNotEmpty()) {
                    setOnClickListener { onKey(label) }
                }
            }
            grid.addView(tv)
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
            val filled = i < entered.length
            (dot.background as android.graphics.drawable.GradientDrawable).setColor(
                if (filled) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT
            )
        }
    }

    private fun shakeAndVibrate() {
        vibrate()
        val anim = ObjectAnimator.ofFloat(dotsContainer, "translationX", 0f, 24f, -24f, 18f, -18f, 8f, -8f, 0f)
        anim.duration = 400
        anim.interpolator = CycleInterpolator(1f)
        anim.start()
        dotsContainer.postDelayed({
            entered.clear()
            refreshDots()
        }, 350)
    }

    private fun vibrate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    }
}
