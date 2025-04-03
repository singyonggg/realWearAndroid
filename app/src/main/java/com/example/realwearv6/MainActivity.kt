package com.example.realwearv6

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.example.realwearv6.databinding.ActivityMainBinding
import com.pedro.rtsp.rtsp.VideoCodec
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

class MainActivity : AppCompatActivity(), ConnectCheckerRtsp {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    companion object {
        const val ACTION_DICTATION = "com.realwear.keyboard.intent.action.DICTATION"
        const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        const val DICTATION_REQUEST_CODE = 34
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkCameraPermission()
    }

    override fun onSupportNavigateUp():Boolean{
        navController = findNavController(R.id.fragmentContainer)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun checkCameraPermission() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onAuthErrorRtsp() {
        Log.e("RTSP", "Authentication error")
    }

    override fun onAuthSuccessRtsp() {
        Log.d("RTSP", "Authentication successful")
    }

    override fun onConnectionFailedRtsp(reason: String) {
        Log.e("RTSP", "Connection failed: $reason")
    }

    override fun onConnectionStartedRtsp(rtspUrl: String) {
        Log.d("RTSP", "Connection started: $rtspUrl")
    }

    override fun onConnectionSuccessRtsp() {
        Log.d("RTSP", "Connection successful")
    }

    override fun onDisconnectRtsp() {
        Log.d("RTSP", "Disconnected from RTSP server")
    }

    override fun onNewBitrateRtsp(bitrate: Long) {
        Log.d("RTSP", "New bitrate: $bitrate")
    }

}
