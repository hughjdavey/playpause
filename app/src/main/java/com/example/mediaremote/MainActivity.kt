package com.example.mediaremote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private var trackDurationMs: Long = 0L

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            tickProgress()
            progressHandler.postDelayed(this, 1000L)
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            runOnUiThread {
                updatePlayPauseIcon(state)
                updateProgressFromState(state)
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            runOnUiThread {
                trackDurationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                updateProgressFromState(activeController?.playbackState)
                updateNowPlaying(metadata)
            }
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
        setupVolumeSlider()
        setupBrightnessSlider()
    }

    override fun onResume() {
        super.onResume()
        syncVolumeSlider()
        syncBrightnessSlider()
        updateSkipButtonLabels()

        if (hasNotificationListenerPermission()) {
            connectToMediaSession()
        } else {
            showPermissionBanner()
            updatePlayPauseIcon(null)
            resetProgress()
            clearNowPlaying()
        }
    }

    override fun onPause() {
        super.onPause()
        stopProgressTicker()
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

                trackDurationMs = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                val state = controller.playbackState
                updatePlayPauseIcon(state)
                updateProgressFromState(state)
                updateNowPlaying(controller.metadata)

                if (state?.state == PlaybackState.STATE_PLAYING) startProgressTicker()
            } else {
                updatePlayPauseIcon(null)
                resetProgress()
                clearNowPlaying()
            }
        } catch (e: SecurityException) {
            showPermissionBanner()
            updatePlayPauseIcon(null)
            resetProgress()
            clearNowPlaying()
        }
    }

    private fun detachMediaController() {
        activeController?.unregisterCallback(mediaCallback)
        activeController = null
        trackDurationMs = 0L
    }

    // ---- Now playing ----

    private fun updateNowPlaying(metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

        if (title.isNullOrBlank() && artist.isNullOrBlank()) {
            clearNowPlaying()
            return
        }

        binding.nowPlayingSection.visibility = View.VISIBLE
        binding.tvTitle.text = title ?: ""
        binding.tvArtist.text = artist ?: ""
        binding.tvTitle.visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvArtist.visibility = if (artist.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun clearNowPlaying() {
        binding.nowPlayingSection.visibility = View.INVISIBLE
        binding.tvTitle.text = ""
        binding.tvArtist.text = ""
    }

    // ---- Progress bar ----

    private fun updateProgressFromState(state: PlaybackState?) {
        if (state == null || trackDurationMs <= 0L) {
            resetProgress()
            return
        }
        val posMs = state.position
        binding.progressBar.max = trackDurationMs.toInt()
        binding.progressBar.progress = posMs.toInt()
        binding.tvElapsed.text = formatDuration(posMs)
        binding.tvTotal.text = formatDuration(trackDurationMs)
        binding.progressSection.visibility = View.VISIBLE
    }

    private fun tickProgress() {
        val state = activeController?.playbackState ?: return
        if (state.state != PlaybackState.STATE_PLAYING) return
        if (trackDurationMs <= 0L) return

        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        val extrapolated = (state.position + state.playbackSpeed * elapsed).toLong()
            .coerceIn(0L, trackDurationMs)

        binding.progressBar.progress = extrapolated.toInt()
        binding.tvElapsed.text = formatDuration(extrapolated)
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressHandler.post(progressTick)
    }

    private fun stopProgressTicker() {
        progressHandler.removeCallbacks(progressTick)
    }

    private fun resetProgress() {
        stopProgressTicker()
        binding.progressSection.visibility = View.INVISIBLE
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    // ---- Buttons ----

    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            val controls = activeController?.transportControls
            if (controls != null) {
                val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
                if (isPlaying) controls.pause() else controls.play()
            } else {
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

    // ---- Play/pause icon ----

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

    // ---- Volume ----

    private fun setupVolumeSlider() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.volumeSlider.max = maxVolume

        binding.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun syncVolumeSlider() {
        binding.volumeSlider.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
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
