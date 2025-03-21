package com.example.realwearv6

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.realwearv6.databinding.FragmentLiveStreamingBinding
import com.pedro.rtplibrary.rtsp.RtspCamera2
import io.socket.client.Socket
import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.inputmethod.InputMethodManager
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
import io.socket.emitter.Emitter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import androidx.annotation.NonNull;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.Properties


class LiveStreamingFragment : Fragment(), ConnectCheckerRtsp {
    private lateinit var binding: FragmentLiveStreamingBinding
    private lateinit var rtspCamera: RtspCamera2
    private var streamUrl: String = ""
    private var deviceID: String = ""
    private var socket: Socket ?= null
    private var inputData: String? = null
    private var inputRoomID: String? = null
    private var targetId: Int? = null
    private var url: String = ""



    companion object {
        const val ACTION_DICTATION = "com.realwear.keyboard.intent.action.DICTATION"
        const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        const val DICTATION_REQUEST_CODE = 34
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentLiveStreamingBinding.inflate(inflater,container, false)
        url = loadConfig("server_ip")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fetch data
        val args = this.arguments
        inputData = args?.getString("userInputIpAddress")
        inputRoomID = args?.getString("userInputRoomID")
        targetId = args?.getInt("targetId") // Get the ID


        if (inputData != null && inputRoomID != null) {
//            checkCameraPermission()
            deviceID = Settings.Secure.getString(requireActivity().contentResolver, Settings.Secure.ANDROID_ID).toString()

            setupRTSPStream()
            onLaunchDictation(targetId, inputData,inputRoomID)
            setupIPSubmitButton()

//            binding.tvResult.text = inputRoomID
        }else{
            Toast.makeText(requireContext(), "Invalid IP Address or Room Name", Toast.LENGTH_SHORT).show()
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigate(R.id.action_liveStreamingFragment_to_homeFragment)
        }
        binding.btnCloseStream.setOnClickListener {
            stopStream()
        }
    }


    // Receive the emitted results from Python script
//    private val onDataReceived = Emitter.Listener { args ->
//        requireActivity().runOnUiThread {
//            val data = args[0] as JSONObject
//            // Handle the received data
//            val faceCount = data.getInt("face_count")
//            val value2 = data.getDouble("detect_time")
//            val formattedDate = java.text.SimpleDateFormat(
//                "yyyy-MM-dd HH:mm:ss",
//                Locale.getDefault()
//            ).format(Date((value2 * 1000).toLong()))  // Multiply by 1000 to convert seconds to milliseconds
//            val comment = data.getString("comment")
//
//            val base64Image = data.getString("image")
//            val decodedString = Base64.decode(base64Image, Base64.DEFAULT)
//            val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
//
//            binding.tvPassValue.text = "Value: $faceCount"
//            binding.tvPassValue2.text = "Detect time: $formattedDate"
//            binding.tvPassValue3.text = "Comment: $comment"
//            binding.ivDisplayImage.setImageBitmap(bitmap)
//        }
//    }


    private fun loadConfig(key:String):String{
        return try {
            val properties = Properties()
            val inputStream = requireContext().assets.open("config.properties")
            properties.load(inputStream)
            properties.getProperty(key, "http://default-ip:5000")  // Default value if not found
        } catch (e: Exception) {
            Log.e("ConfigError", "Error loading config.properties: ${e.message}")
            "http://default-ip:5000"  // Fallback IP
        }
    }

    // Receive the emitted results from Python script
    private val onDataReceived = Emitter.Listener { args ->
        requireActivity().runOnUiThread {
            val data = args[0] as JSONObject
            // Handle the received data
            val sequences = data.getString("sequence")
            val predicted_class = data.getString("predicted_class")
            val message = data.getString("message")

//            val base64Image = data.getString("image")
//            val decodedString = Base64.decode(base64Image, Base64.DEFAULT)
//            val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

            binding.tvPassValue.text = "Sequences: $sequences"
            binding.tvPassValue2.text = "Model Class: $predicted_class"
            binding.tvPassValue3.text = "Comment: $message"
//            binding.ivDisplayImage.setImageBitmap(bitmap)
        }
    }

//    private fun checkCameraPermission() {
//        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
//        val missingPermissions = permissions.filter {
//            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
//        }
//
//        if (missingPermissions.isNotEmpty()) {
//            ActivityCompat.requestPermissions(requireActivity(), missingPermissions.toTypedArray(),
//                CAMERA_PERMISSION_REQUEST_CODE
//            )
//        } else {
//            setupRTSPStream()
//        }
//    }


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
        // checkCameraPermission()
        submitIPAndStartStream(inputData,inputRoomID)
    }

    private fun submitIPAndStartStream(inputData: String?,inputRoomID:String?) {
        val userInputIpAddress = inputData?.trim()
        val userRoom = inputRoomID?.trim()

        if (!userInputIpAddress.isNullOrEmpty() && !userRoom.isNullOrEmpty()) {
            // binding.userInputIPAddress.text.clear()
            streamUrl = "rtsp://$userInputIpAddress:8554/$deviceID"

            // POST to flask server
            sendPostRequest("post_device_info", deviceID, streamUrl,userInputIpAddress ?: "", userRoom ?: "")

            socket = IO.socket("http://$userInputIpAddress:5000")
            socket?.connect()
            socket?.emit("join", JSONObject().put("room", userRoom))
            Toast.makeText(requireContext(), deviceID, Toast.LENGTH_SHORT).show()
            socket?.on(deviceID, onDataReceived)

            // Save the streamUrl to SharedPreferences
            val sharedPreferences = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("streamUrl", streamUrl).apply()

            binding.tvRtspLink.visibility = View.VISIBLE
            binding.tvRtspLink.text = streamUrl
            Toast.makeText(requireContext(), "Stream URL: $streamUrl", Toast.LENGTH_SHORT).show()
            startCameraAndStream(streamUrl)

        } else {
            Toast.makeText(requireContext(), "IP Address or Room Name is missing.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendPostRequest(method: String, deviceID: String, streamUrl: String, ipAddress: String, roomId: String) {
        val fullURL = "$url/$method"

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val formBody = FormBody.Builder()
            .add("device_id", deviceID)
            .add("stream_url",streamUrl)
            .add("ip_address", ipAddress)
            .add("room_id", roomId)
            .build()

        val request = Request.Builder()
            .url(fullURL)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()

                // Run on UI thread to show error toast
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseData = it.body?.string() ?: "No Response"

                    // Run on UI thread to show response
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), responseData, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun startCameraAndStream(streamUrl: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.textureView.surfaceTexture?.let { surfaceTexture ->
                    Preview.SurfaceProvider { request ->
                        request.provideSurface(Surface(surfaceTexture), ContextCompat.getMainExecutor(requireContext())) {}
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
                Toast.makeText(requireContext(), "Failed to prepare RTSP stream", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
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

            Toast.makeText(requireContext(), "Stream stopped and exit room", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MainActivity.CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupRTSPStream()
            } else {
                Toast.makeText(requireContext(), "Camera and audio permissions denied", Toast.LENGTH_SHORT).show()
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
        socket?.let {
            if (it.connected()) {
                it.disconnect()
                it.off("data", onDataReceived)
            }
        }
    }

    // Real Wear
    fun onLaunchDictation(targetId: Int?, inputData: String?, inputRoomID: String?) {
        val intent = Intent(MainActivity.ACTION_DICTATION).apply {
            if (targetId != null) {
                putExtra("targetId", targetId) // Pass the ID of the target field
            }
            if (inputData != null) {
                putExtra("text", inputData)
            }
            if (inputRoomID != null) {
                putExtra("text", inputRoomID)
            }
        }
        try {
            startActivityForResult(intent, MainActivity.DICTATION_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "RealWear Dictation Service not found", Toast.LENGTH_SHORT).show()
        }
    }

}