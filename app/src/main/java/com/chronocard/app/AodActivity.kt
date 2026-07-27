package com.chronocard.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class AodActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var timerSecond = 1
    private lateinit var tvTimer: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTrack: TextView
    private lateinit var root: FrameLayout

    private val tickRunnable = object : Runnable {
        override fun run() {
            tvTimer.text = String.format("%d:%02d", 3, timerSecond)
            timerSecond = if (timerSecond >= 13) 1 else timerSecond + 1
            handler.postDelayed(this, 1500)
        }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aod)
        goFullscreen()

        root = findViewById(R.id.root)
        tvTimer = findViewById(R.id.tvTimer)
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        tvTrack = findViewById(R.id.tvTrack)
        tvTrack.text = getString(R.string.np_track)

        updateClock()
        handler.post(tickRunnable)
        handler.post(clockRunnable)
        startMarquee()

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onScreenTap(event.x, event.y)
            }
            true
        }
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

    private fun startMarquee() {
        tvTrack.post {
            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            val textWidth = tvTrack.paint.measureText(tvTrack.text.toString())
            val startX = screenWidth
            val endX = -textWidth
            tvTrack.x = startX
            val distance = startX - endX
            val speedPxPerSec = 90f * density
            val durationMs = ((distance / speedPxPerSec) * 1000).toLong().coerceAtLeast(3000L)
            val anim = ObjectAnimator.ofFloat(tvTrack, "x", startX, endX)
            anim.duration = durationMs
            anim.interpolator = LinearInterpolator()
            anim.repeatCount = ValueAnimator.INFINITE
            anim.start()
        }
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        tvClock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now.time)
        tvDate.text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now.time)
    }

    private fun onScreenTap(x: Float, y: Float) {
        val rank = timerSecond.let { current ->
            if (current == 1) 13 else current - 1
        }
        val quadrant = when {
            x < root.width / 2f && y < root.height / 2f -> CardUtils.Quadrant.TOP_LEFT
            x >= root.width / 2f && y < root.height / 2f -> CardUtils.Quadrant.TOP_RIGHT
            x < root.width / 2f && y >= root.height / 2f -> CardUtils.Quadrant.BOTTOM_LEFT
            else -> CardUtils.Quadrant.BOTTOM_RIGHT
        }
        val suit = CardUtils.suitFromQuadrant(quadrant)
        val card = CardUtils.Card(rank, suit)
        handler.removeCallbacksAndMessages(null)
        LockscreenActivity.launch(this, card)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
