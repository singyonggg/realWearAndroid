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
import com.example.realwearv6.databinding.ActivityMainBinding
import com.pedro.rtplibrary.rtsp.RtspCamera2
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
    private lateinit var rtspCamera: RtspCamera2
    private var streamUrl: String = ""
    private var deviceID: String = ""
    private var userInputIpAddress:String = ""
    private lateinit var socket: Socket

    companion object {
        const val ACTION_DICTATION = "com.realwear.keyboard.intent.action.DICTATION"
        const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        const val DICTATION_REQUEST_CODE = 34
    }


    init {
        try {
            socket = IO.socket("http://$userInputIpAddress:4999")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Real Wear
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityMainBinding.inflate(layoutInflater)


        setContentView(binding.root)

        checkCameraPermission()
        deviceID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID).toString()

        binding.btnIPSubmit.setOnClickListener {
            onLaunchDictation(binding.userInputIPAddress)
            setupIPSubmitButton()
        }


        binding.btnCloseStream.setOnClickListener {
            stopStream()
        }

        binding.imgBtnSetting.setOnClickListener {
            // Create an intent to navigate to SettingsActivity
            val intent = Intent(this, SettingsActivity::class.java)
            // Start the activity
            startActivity(intent)
        }

        socket.connect()
        socket.emit("join", JSONObject().put("room", deviceID))
        // Listen for data from the server
        socket.on("data", onDataReceived)

    }

    private val onDataReceived = Emitter.Listener { args ->
        runOnUiThread {
            val data = args[0] as JSONObject
            // Handle the received data
            val faceCount = data.getInt("face_count")

            val value2 = data.getDouble("detect_time")
            val formattedDate = java.text.SimpleDateFormat(
                  "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                    ).format(Date((value2 * 1000).toLong()))  // Multiply by 1000 to convert seconds to milliseconds
            val comment = data.getString("comment")

            val imgByteArray = data.get("image") as ByteArray  // Get the byte array of the image
            val bitmap = BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)

            // Update your UI with the received data
            // For example, display the face count in a TextView
            binding.tvPassValue.text = "Value: $faceCount"
            binding.tvPassValue2.text = "Detect time: $formattedDate"
            binding.tvPassValue3.text = "Comment: $comment"
            binding.ivDisplayImage.setImageBitmap(bitmap)
        }
    }

    private fun checkCameraPermission() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), CAMERA_PERMISSION_REQUEST_CODE)
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
        checkCameraPermission()
        submitIPAndStartStream()
    }

    private fun submitIPAndStartStream() {
        userInputIpAddress = binding.userInputIPAddress.text.toString().trim()

        if (userInputIpAddress.isNotEmpty()) {
            binding.userInputIPAddress.text.clear()

            streamUrl = "rtsp://$userInputIpAddress:8554/$deviceID"

            // Save the streamUrl to SharedPreferences

            val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            sharedPreferences.edit().putString("streamUrl", streamUrl).apply()

            binding.tvRtspLink.visibility = View.VISIBLE
            binding.tvRtspLink.text = streamUrl
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

            // Configure RTSP with audio
            if (rtspCamera.prepareAudio() && rtspCamera.prepareVideo(1280, 720, 10, 1200 * 1024, 1, 1)) {
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


            val textureView = binding.textureView
            textureView.surfaceTexture?.let {
                textureView.surfaceTextureListener?.onSurfaceTextureDestroyed(
                    it
                )
            }

            Toast.makeText(this, "Stream stopped and exit room", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupRTSPStream()
            } else {
                Toast.makeText(this, "Camera and audio permissions denied", Toast.LENGTH_SHORT).show()
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
        if (socket.connected()) {
            socket.disconnect() // Ensure the WebSocket is disconnected
        }
    }

    // Real Wear
    fun onLaunchDictation(targetField: EditText) {
        val intent = Intent(ACTION_DICTATION).apply {
            putExtra("targetId", targetField.id) // ID of the target text field
            putExtra("text", targetField.text.toString()) // Optional: Prefill with current text
        }
        try {
            startActivityForResult(intent, DICTATION_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "RealWear Dictation Service not found", Toast.LENGTH_SHORT).show()
        }
    }



}
