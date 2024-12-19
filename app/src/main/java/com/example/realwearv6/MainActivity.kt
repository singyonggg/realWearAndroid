package com.example.realwearv6

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.realwearv6.databinding.ActivityMainBinding
import com.pedro.rtplibrary.rtsp.RtspCamera2
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import android.provider.Settings
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.core.content.ContentProviderCompat.requireContext


class MainActivity : AppCompatActivity(), ConnectCheckerRtsp {
    private lateinit var binding: ActivityMainBinding
    private lateinit var rtspCamera: RtspCamera2
    private var streamUrl: String = ""

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // supportActionBar?.hide()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkCameraPermission()

        binding.btnIPSubmit.setOnClickListener {
            setupIPSubmitButton()
        }


        binding.btnCloseStream.setOnClickListener {
            stopStream()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            setupRTSPStream()
        }
    }

    // This used to prepare the Texture View to host the camera
    private fun setupRTSPStream() {
        rtspCamera = RtspCamera2(binding.textureView, this)

        binding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (streamUrl.isNotEmpty()) {
                    startCameraAndStream(streamUrl)
                }
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                stopStream()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    // Stream Start Button
    private fun setupIPSubmitButton() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            // Request camera permission
//            ActivityCompat.requestPermissions(
//                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
//            )
//        } else {
//
//        }
        checkCameraPermission()
        submitIPAndStartStream()
    }

    private fun submitIPAndStartStream() {
        val userInputIpAddress = binding.userInputIPAddress.text.toString().trim()

        if (userInputIpAddress.isNotEmpty()) {
            binding.userInputIPAddress.text.clear()
            val uniqueDeviceID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID).toString()
            streamUrl = "rtsp://$userInputIpAddress/$uniqueDeviceID"
//            Log.d("RTSP", "Stream URL: $streamUrl") // Need used dialog bos maybe
            Toast.makeText(this, "Stream URL: $streamUrl", Toast.LENGTH_SHORT).show()
            startCameraAndStream(streamUrl)
        } else {
            Toast.makeText(this, "Please provide the correct IP Address and Port number", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCameraAndStream(streamUrl: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Unbind all existing use cases before adding a new one
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.textureView.surfaceTexture?.let { surfaceTexture ->
                    Preview.SurfaceProvider { request ->
                        request.provideSurface(Surface(surfaceTexture), ContextCompat.getMainExecutor(this)) {}
                    }
                })

            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(this, cameraSelector, preview)

            // Start RTSP Stream
            if (rtspCamera.prepareVideo(1280, 720, 30, 1200 * 1024, 2, 0)) {
                rtspCamera.startStream(streamUrl)
                Log.d("RTSP", "Streaming started at $streamUrl")
            } else {
                Toast.makeText(this, "Failed to prepare RTSP stream", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopStream() {
        if (rtspCamera.isStreaming) {
            rtspCamera.stopStream()
            rtspCamera.stopPreview()

            // Notify that the TextureView's surface is no longer needed
            val textureView = binding.textureView
            textureView.surfaceTexture?.let {
                textureView.surfaceTextureListener?.onSurfaceTextureDestroyed(
                    it
                )
            }

            Toast.makeText(this, "Stream stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupRTSPStream()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
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

    override fun onDestroy() {
        super.onDestroy()
        stopStream()
    }
}
