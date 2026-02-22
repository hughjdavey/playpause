package com.example.mediaremote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.mediaremote.databinding.ActivityMainBinding
import com.example.mediaremote.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioManager: AudioManager

    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen awake while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupButtons()
        setupBrightnessSlider()
        updatePlayPauseIcon()
    }

    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            isPlaying = !isPlaying
            updatePlayPauseIcon()
        }

        binding.btnSkipBack.setOnClickListener {
            val skipSeconds = getSkipSeconds()
            repeat(skipSeconds / 10) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            }
            // For apps that support seek back via key long press, also try skip back
            // Use a direct broadcast approach for maximum compatibility
            skipMedia(-skipSeconds)
        }

        binding.btnSkipForward.setOnClickListener {
            val skipSeconds = getSkipSeconds()
            skipMedia(skipSeconds)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateSkipButtonLabels()
    }

    private fun updateSkipButtonLabels() {
        val secs = getSkipSeconds()
        binding.btnSkipBack.text = "−${secs}s"
        binding.btnSkipForward.text = "+${secs}s"
    }

    private fun getSkipSeconds(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getInt("skip_seconds", 20)
    }

    private fun skipMedia(seconds: Int) {
        // Strategy: send KEYCODE_MEDIA_STEP_FORWARD/BACKWARD for supported apps,
        // and also simulate rewind/fast-forward keypresses.
        // Most podcast apps (Pocket Casts, AntennaPod, etc.) respond to these.
        if (seconds > 0) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        } else {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun updatePlayPauseIcon() {
        if (isPlaying) {
            binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_pause)
            binding.btnPlayPause.text = "Pause"
        } else {
            binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
            binding.btnPlayPause.text = "Play"
        }
    }

    private fun setupBrightnessSlider() {
        binding.brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setBrightness(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun syncBrightnessSlider() {
        // Read current screen brightness
        try {
            val currentBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            binding.brightnessSlider.progress = currentBrightness
        } catch (e: Settings.SettingNotFoundException) {
            binding.brightnessSlider.progress = 128
        }
    }

    private fun setBrightness(value: Int) {
        // First try to set via window attributes (doesn't need WRITE_SETTINGS)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = value / 255f
        window.attributes = layoutParams

        // Also try to set system brightness if permission granted
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
        }
    }

    override fun onResume() {
        super.onResume()
        syncBrightnessSlider()
        updateSkipButtonLabels()
    }
}
