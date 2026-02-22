package com.example.mediaremote.settings

import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
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

        val brightnessPermPref = findPreference<Preference>("grant_brightness_permission")
        brightnessPermPref?.setOnPreferenceClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = android.net.Uri.parse("package:${requireContext().packageName}")
            startActivity(intent)
            true
        }

        updatePermissionSummary()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionSummary()
    }

    private fun updatePermissionSummary() {
        val pref = findPreference<Preference>("grant_brightness_permission")
        if (Settings.System.canWrite(requireContext())) {
            pref?.summary = "✓ Permission granted — brightness slider also changes system brightness"
        } else {
            pref?.summary = "Tap to grant — without this the slider only affects brightness while the app is open"
        }
    }
}
