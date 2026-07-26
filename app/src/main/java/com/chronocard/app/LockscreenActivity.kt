package com.chronocard.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class LockscreenActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_RANK = "extra_rank"
        private const val EXTRA_SUIT = "extra_suit"
        private const val HOLD_MS = 500L
        private const val INACTIVITY_TIMEOUT_MS = 5000L

        fun launch(ctx: Context, card: CardUtils.Card) {
            val i = Intent(ctx, LockscreenActivity::class.java)
            i.putExtra(EXTRA_RANK, card.rank)
            i.putExtra(EXTRA_SUIT, card.suit)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null
    private var unlocked = false
    private lateinit var tvTrack: TextView
    private lateinit var tvTimer: TextView

    private val inactivityRunnable = Runnable { revertToInput() }

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lockscreen)

        val rank = intent.getIntExtra(EXTRA_RANK, 1)
        val suit = intent.getIntExtra(EXTRA_SUIT, 1)
        val card = CardUtils.Card(rank, suit)

        val ivBackground = findViewById<ImageView>(R.id.ivBackground)
        SetupPrefs.getBackground(this)?.let { path ->
            ivBackground.setImageURI(Uri.fromFile(java.io.File(path)))
        }

        tvTrack = findViewById(R.id.tvTrack)
        tvTrack.text = getString(R.string.np_track)
        startMarquee()

        tvTimer = findViewById(R.id.tvTimer)
        tvTimer.text = CardUtils.confirmationTimerLabel(card)

        updateClock()
        handler.post(clockRunnable)

        runEntranceAnimation()
        setupFingerprintHold(card)
        resetInactivityTimer()
    }

    /** Any touch anywhere on the lockscreen counts as activity and pushes the timeout back. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            resetInactivityTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun resetInactivityTimer() {
        handler.removeCallbacks(inactivityRunnable)
        handler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS)
    }

    private fun revertToInput() {
        if (unlocked) return
        holdRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, PerformActivity::class.java))
        overridePendingTransition(0, 0)
        finish()
    }

    private fun startMarquee() {
        tvTrack.post {
            val parentWidth = (tvTrack.parent as android.view.View).width.toFloat().takeIf { it > 0 } ?: 300f
            val textWidth = tvTrack.paint.measureText(tvTrack.text.toString())
            tvTrack.x = parentWidth
            val anim = ObjectAnimator.ofFloat(tvTrack, "x", parentWidth, -textWidth)
            anim.duration = 6000L + (textWidth * 8).toLong()
            anim.interpolator = LinearInterpolator()
            anim.repeatCount = android.animation.ValueAnimator.INFINITE
            anim.start()
        }
    }

    private fun runEntranceAnimation() {
        val timeGroup = findViewById<android.view.View>(R.id.timeGroup)
        val ivCamera = findViewById<ImageView>(R.id.ivCamera)
        val ivPhone = findViewById<ImageView>(R.id.ivPhone)

        timeGroup.translationY = 60f
        timeGroup.alpha = 0f
        val rise = ObjectAnimator.ofFloat(timeGroup, "translationY", 60f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(timeGroup, "alpha", 0f, 1f)
        val camFade = ObjectAnimator.ofFloat(ivCamera, "alpha", 0f, 1f)
        val phoneFade = ObjectAnimator.ofFloat(ivPhone, "alpha", 0f, 1f)

        val set = AnimatorSet()
        set.playTogether(rise, fadeIn)
        set.duration = 450
        set.startDelay = 80
        set.start()

        val iconSet = AnimatorSet()
        iconSet.playTogether(camFade, phoneFade)
        iconSet.duration = 400
        iconSet.startDelay = 350
        iconSet.start()
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        findViewById<TextView>(R.id.tvClock).text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
        findViewById<TextView>(R.id.tvDate).text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now.time)
    }

    private fun setupFingerprintHold(card: CardUtils.Card) {
        val fp = findViewById<ImageView>(R.id.ivFingerprint)
        fp.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val r = Runnable { completeUnlock(card) }
                    holdRunnable = r
                    handler.postDelayed(r, HOLD_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdRunnable?.let { handler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun completeUnlock(card: CardUtils.Card) {
        unlocked = true
        handler.removeCallbacksAndMessages(null)
        // Perform the actual backdated insert, then close out silently.
        GalleryInserter.performInsertForCard(this, card)
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
