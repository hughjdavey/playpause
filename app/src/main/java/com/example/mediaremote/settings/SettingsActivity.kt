package com.example.mediaremote.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.mediaremote.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("grant_media_permission")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            true
        }

        findPreference<Preference>("grant_brightness_permission")?.setOnPreferenceClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${requireContext().packageName}")
            startActivity(intent)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }

    private fun updateSummaries() {
        val ctx = requireContext()

        val notificationListeners = Settings.Secure.getString(
            ctx.contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val hasMediaPermission = notificationListeners.contains(ctx.packageName)

        findPreference<Preference>("grant_media_permission")?.summary = if (hasMediaPermission) {
            "✓ Permission granted — play/pause button syncs with real playback state"
        } else {
            "Tap to grant — without this the button can't read real playback state"
        }

        findPreference<Preference>("grant_brightness_permission")?.summary = if (Settings.System.canWrite(ctx)) {
            "✓ Permission granted — brightness slider also changes system brightness"
        } else {
            "Tap to grant — without this the slider only affects brightness while the app is open"
        }
    }
}
