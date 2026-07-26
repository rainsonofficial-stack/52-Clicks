package com.chronocard.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Invisible router: sends the performer straight into the chosen input mode. */
class PerformActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = when (SetupPrefs.getMode(this)) {
            SetupPrefs.Mode.PASSCODE -> PasscodeActivity::class.java
            SetupPrefs.Mode.TAP -> AodActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
