package com.example.realwearv6

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.content.Intent
import android.widget.ImageButton

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the screen orientation to landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        setContentView(R.layout.activity_settings)

        // Navigate back to home
        val backButton: ImageButton = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Optionally finish the current activity
        }

        // Add the SettingsFragment
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()

    }
}