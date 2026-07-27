package com.chronocard.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LockscreenActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_RANK = "extra_rank"
        private const val EXTRA_SUIT = "extra_suit"
        private const val HOLD_MS = 500L
        private const val INACTIVITY_TIMEOUT_MS = 10000L
        private const val BURST_MS = 800L

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
    private var rippleAnimator: ValueAnimator? = null
    private var unlocked = false
    private lateinit var tvTrack: TextView
    private lateinit var tvTimer: TextView
    private lateinit var ivFingerprint: ImageView
    private lateinit var rippleView: View

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
        goFullscreen()

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

        ivFingerprint = findViewById(R.id.ivFingerprint)
        rippleView = findViewById(R.id.rippleView)

        updateClock()
        handler.post(clockRunnable)
        runEntranceAnimation()
        setupFingerprintHold(card)
        resetInactivityTimer()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) resetInactivityTimer()
        return super.dispatchTouchEvent(ev)
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
        val container = tvTrack.parent as View
        val textWidth = tvTrack.paint.measureText(tvTrack.text.toString())
        val textWidthPx = kotlin.math.ceil(textWidth).toInt() + 4

        val lp = tvTrack.layoutParams
        lp.width = textWidthPx
        tvTrack.layoutParams = lp

        container.doOnLayout {
            val density = resources.displayMetrics.density
            val containerWidth = container.width.toFloat()
            val startX = containerWidth
            val endX = -textWidthPx.toFloat()
            tvTrack.x = startX
            val distance = startX - endX
            val speedPxPerSec = 40f * density
            val durationMs = ((distance / speedPxPerSec) * 1000).toLong().coerceAtLeast(4000L)
            val anim = ObjectAnimator.ofFloat(tvTrack, "x", startX, endX)
            anim.duration = durationMs
            anim.interpolator = LinearInterpolator()
            anim.repeatCount = ValueAnimator.INFINITE
            anim.start()
        }
    }

    private fun runEntranceAnimation() {
        val timeGroup = findViewById<View>(R.id.timeGroup)
        val ivCamera = findViewById<ImageView>(R.id.ivCamera)
        val ivPhone = findViewById<ImageView>(R.id.ivPhone)

        // Rises UP from AOD position (15px below) to lockscreen position
        timeGroup.translationY = 15f
        timeGroup.alpha = 0f

        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(timeGroup, "translationY", 15f, 0f),
            ObjectAnimator.ofFloat(timeGroup, "alpha", 0f, 1f)
        )
        set.duration = 450
        set.interpolator = AccelerateDecelerateInterpolator()
        set.startDelay = 80
        set.start()

        val iconSet = AnimatorSet()
        iconSet.playTogether(
            ObjectAnimator.ofFloat(ivCamera, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(ivPhone, "alpha", 0f, 1f)
        )
        iconSet.duration = 400
        iconSet.startDelay = 350
        iconSet.start()
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        findViewById<TextView>(R.id.tvClock).text =
            SimpleDateFormat("h:mm", Locale.getDefault()).format(now.time)
        findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now.time)
    }

    private fun setupFingerprintHold(card: CardUtils.Card) {
        ivFingerprint.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRipple()
                    val r = Runnable { completeUnlock(card) }
                    holdRunnable = r
                    handler.postDelayed(r, HOLD_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdRunnable?.let { handler.removeCallbacks(it) }
                    stopRipple()
                    true
                }
                else -> false
            }
        }
    }

    private fun startRipple() {
        rippleView.visibility = View.VISIBLE
        rippleView.scaleX = 1f
        rippleView.scaleY = 1f
        rippleView.alpha = 0.5f
        rippleAnimator = ValueAnimator.ofFloat(1f, 2.8f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                rippleView.scaleX = v
                rippleView.scaleY = v
                rippleView.alpha = (2.8f - v) / 1.8f * 0.5f
            }
            start()
        }
    }

    private fun stopRipple() {
        rippleAnimator?.cancel()
        rippleView.visibility = View.INVISIBLE
    }

    /**
     * The 1s ripple burst is purely visual and runs on its own. The unlock
     * sound is intentionally started in the SAME call as the home/close call,
     * so the "click" and the screen closing happen together instead of the
     * sound playing first with a lag before the app actually closes.
     */
    private fun completeUnlock(card: CardUtils.Card) {
        unlocked = true
        handler.removeCallbacksAndMessages(null)

        rippleAnimator?.cancel()
        rippleView.visibility = View.VISIBLE
        rippleView.scaleX = 1f
        rippleView.scaleY = 1f
        rippleView.alpha = 0.7f
        val burst = AnimatorSet()
        burst.playTogether(
            ObjectAnimator.ofFloat(rippleView, "scaleX", 1f, 5f),
            ObjectAnimator.ofFloat(rippleView, "scaleY", 1f, 5f),
            ObjectAnimator.ofFloat(rippleView, "alpha", 0.7f, 0f)
        )
        burst.duration = BURST_MS
        burst.start()

        handler.postDelayed({
            playUnlockSound() // fires right alongside the home/close call below
            GalleryInserter.performInsertForCard(this, card)
            goHome()
        }, BURST_MS + 50)
    }

    /**
     * Uses the application context and self-releases on completion, rather
     * than being tied to this Activity's onDestroy(). Since playback starts
     * in the same instant the Activity is finishing, tying its lifecycle to
     * onDestroy() would cut the sound off almost immediately.
     */
    private fun playUnlockSound() {
        try {
            val afd = applicationContext.assets.openFd("unlock.mp3")
            val player = MediaPlayer()
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); true }
            player.prepare()
            player.start()
        } catch (_: Exception) {
            // No sound file present — skip silently
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Explicitly navigates to the real home screen before closing, rather
     * than just finishing back to whatever the launcher's last state was.
     */
    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAffinity()
    }
}
