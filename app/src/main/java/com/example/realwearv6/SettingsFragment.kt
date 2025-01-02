package com.example.realwearv6

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment: PreferenceFragmentCompat (){
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from the XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}