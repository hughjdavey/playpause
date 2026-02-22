package com.example.mediaremote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.mediaremote.databinding.ActivityMainBinding
import com.example.mediaremote.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSessionManager: MediaSessionManager

    private var activeController: MediaController? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Fires whenever real playback state changes — headphones, other app UI, etc.
            runOnUiThread { updatePlayPauseIcon(state) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        setupButtons()
        setupBrightnessSlider()
    }

    override fun onResume() {
        super.onResume()
        syncBrightnessSlider()
        updateSkipButtonLabels()

        if (hasNotificationListenerPermission()) {
            connectToMediaSession()
        } else {
            showPermissionBanner()
            updatePlayPauseIcon(null)
        }
    }

    override fun onPause() {
        super.onPause()
        detachMediaController()
    }

    // ---- Permission ----

    private fun hasNotificationListenerPermission(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    // ---- Media session ----

    private fun connectToMediaSession() {
        detachMediaController()
        binding.permissionBanner.visibility = View.GONE

        try {
            val listenerComponent = ComponentName(this, MediaListenerService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(listenerComponent)
            val controller = controllers.firstOrNull()
            if (controller != null) {
                activeController = controller
                controller.registerCallback(mediaCallback)
                updatePlayPauseIcon(controller.playbackState)
            } else {
                updatePlayPauseIcon(null)
            }
        } catch (e: SecurityException) {
            showPermissionBanner()
            updatePlayPauseIcon(null)
        }
    }

    private fun detachMediaController() {
        activeController?.unregisterCallback(mediaCallback)
        activeController = null
    }

    // ---- Buttons ----

    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            val controls = activeController?.transportControls
            if (controls != null) {
                val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
                if (isPlaying) controls.pause() else controls.play()
                // Icon updates via the mediaCallback — no need to flip it manually
            } else {
                // No session connected — fire raw key event as fallback
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }

        binding.btnSkipBack.setOnClickListener {
            val controls = activeController?.transportControls
            if (controls != null) {
                val pos = activeController?.playbackState?.position ?: 0L
                controls.seekTo(maxOf(0L, pos - getSkipSeconds() * 1000L))
            } else {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            }
        }

        binding.btnSkipForward.setOnClickListener {
            val controls = activeController?.transportControls
            if (controls != null) {
                val pos = activeController?.playbackState?.position ?: 0L
                controls.seekTo(pos + getSkipSeconds() * 1000L)
            } else {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.permissionBanner.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        updateSkipButtonLabels()
    }

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun updateSkipButtonLabels() {
        val secs = getSkipSeconds()
        binding.btnSkipBack.text = "−${secs}s"
        binding.btnSkipForward.text = "+${secs}s"
    }

    private fun getSkipSeconds(): Int {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt("skip_seconds", 20)
    }

    // ---- UI ----

    private fun updatePlayPauseIcon(state: PlaybackState?) {
        val playing = state?.state == PlaybackState.STATE_PLAYING
        if (playing) {
            binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_pause)
            binding.btnPlayPause.text = "Pause"
        } else {
            binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
            binding.btnPlayPause.text = "Play"
        }
    }

    private fun showPermissionBanner() {
        binding.permissionBanner.visibility = View.VISIBLE
    }

    // ---- Brightness ----

    private fun setupBrightnessSlider() {
        binding.brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setBrightness(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun syncBrightnessSlider() {
        try {
            val b = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            binding.brightnessSlider.progress = b
        } catch (e: Settings.SettingNotFoundException) {
            binding.brightnessSlider.progress = 128
        }
    }

    private fun setBrightness(value: Int) {
        val lp = window.attributes
        lp.screenBrightness = value / 255f
        window.attributes = lp
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }
    }
}
