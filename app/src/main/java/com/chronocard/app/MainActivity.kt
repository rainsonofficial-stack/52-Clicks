package com.chronocard.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: CardGridAdapter
    private var pendingCardKey: String? = null
    private var customCalendar: Calendar = Calendar.getInstance()

    private val pickCardImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingCardKey ?: return@registerForActivityResult
        if (uri != null) {
            val path = copyToInternal(uri, "card_$key.jpg")
            SetupPrefs.setImagePath(this, key, path)
            adapter.setThumb(key, path)
            refreshUploadedCount()
        }
    }

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = copyToInternal(uri, "background.jpg")
            SetupPrefs.setBackground(this, path)
            findViewById<android.widget.TextView>(R.id.tvBackgroundStatus).text = "Background set"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCards)
        rv.layoutManager = GridLayoutManager(this, 5)
        adapter = CardGridAdapter(CardUtils.allKeys()) { key ->
            pendingCardKey = key
            pickCardImage.launch("image/*")
        }
        rv.adapter = adapter
        // preload existing thumbs
        val existing = SetupPrefs.getImageMap(this)
        existing.forEach { (k, path) -> adapter.setThumb(k, path) }
        refreshUploadedCount()

        findViewById<android.widget.Button>(R.id.btnBackground).setOnClickListener {
            pickBackground.launch("image/*")
        }
        SetupPrefs.getBackground(this)?.let {
            findViewById<android.widget.TextView>(R.id.tvBackgroundStatus).text = "Background set"
        }

        val rgBackdate = findViewById<android.widget.RadioGroup>(R.id.rgBackdate)
        val btnCustomTime = findViewById<android.widget.Button>(R.id.btnCustomTime)
        rgBackdate.setOnCheckedChangeListener { _, checkedId ->
            btnCustomTime.visibility = if (checkedId == R.id.rbCustom) android.view.View.VISIBLE else android.view.View.GONE
        }
        (findViewById<RadioButton>(R.id.rbH3)).isChecked = true

        btnCustomTime.setOnClickListener { showCustomTimePicker() }

        findViewById<RadioButton>(R.id.rbPasscode).isChecked = true

        findViewById<android.widget.Button>(R.id.btnPerform).setOnClickListener {
            saveBackdateSelection(rgBackdate.checkedRadioButtonId)
            saveModeSelection()
            startActivity(Intent(this, PerformActivity::class.java))
        }
    }

    private fun refreshUploadedCount() {
        val count = SetupPrefs.getImageMap(this).size
        findViewById<android.widget.TextView>(R.id.tvUploadedCount).text = "$count / 52 uploaded"
    }

    private fun saveBackdateSelection(checkedId: Int) {
        when (checkedId) {
            R.id.rbH3 -> SetupPrefs.setBackdate(this, SetupPrefs.Backdate.H3)
            R.id.rbH10 -> SetupPrefs.setBackdate(this, SetupPrefs.Backdate.H10)
            R.id.rbH24 -> SetupPrefs.setBackdate(this, SetupPrefs.Backdate.H24)
            R.id.rbD3 -> SetupPrefs.setBackdate(this, SetupPrefs.Backdate.D3)
            R.id.rbCustom -> SetupPrefs.setBackdate(this, SetupPrefs.Backdate.CUSTOM, customCalendar.timeInMillis)
            else -> SetupPrefs.setBackdate(this, SetupPrefs.Backdate.H3)
        }
    }

    private fun saveModeSelection() {
        val mode = if (findViewById<RadioButton>(R.id.rbTap).isChecked) SetupPrefs.Mode.TAP else SetupPrefs.Mode.PASSCODE
        SetupPrefs.setMode(this, mode)
    }

    private fun showCustomTimePicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            customCalendar.set(y, m, d)
            TimePickerDialog(this, { _, h, min ->
                customCalendar.set(Calendar.HOUR_OF_DAY, h)
                customCalendar.set(Calendar.MINUTE, min)
                Toast.makeText(this, "Custom time set", Toast.LENGTH_SHORT).show()
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun copyToInternal(uri: Uri, filename: String): String {
        val outFile = File(filesDir, filename)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return outFile.absolutePath
    }
}
