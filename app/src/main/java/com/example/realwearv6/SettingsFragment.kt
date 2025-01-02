package com.example.realwearv6

import android.os.Bundle
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment: PreferenceFragmentCompat (){
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from the XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // FInd the 'copy_to_clipboard' preference
        val copyToClipboardPreference: Preference? = findPreference("copy_to_clipboard")

        // Set click listener for 'copy_to_clipboard'
        copyToClipboardPreference?.setOnPreferenceClickListener {
            val sharedPreferences = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            val streamUrl = sharedPreferences.getString("streamUrl", "No URL found")

            if (!streamUrl.isNullOrEmpty()){
                // Copy the streamUrl to user clipboard
                val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("Stream URL", streamUrl)
                clipboardManager.setPrimaryClip(clip)

                Toast.makeText(requireContext(), "Stream URL copied to clipboard!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "No stream URL found to copy.", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
}