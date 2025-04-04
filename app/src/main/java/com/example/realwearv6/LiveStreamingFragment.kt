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
import org.json.JSONException
import java.util.Properties

class LiveStreamingFragment : Fragment(), ConnectCheckerRtsp {
    private lateinit var binding: FragmentLiveStreamingBinding
    private lateinit var rtspCamera: RtspCamera2
    private var streamUrl: String = ""
    private var deviceID: String = ""
    private var socket: Socket ?= null
    private var inputData: String? = null
    private var inputRoomName: String? = null
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
        binding = FragmentLiveStreamingBinding.inflate(inflater,container, false)
        url = loadConfig("server_ip")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fetch data
        val args = this.arguments
        inputData = args?.getString("userInputIpAddress")
        inputRoomName = args?.getString("userInputRoomName")

        if (inputData != null && inputRoomName != null) {
            deviceID = Settings.Secure.getString(requireActivity().contentResolver, Settings.Secure.ANDROID_ID).toString()

            // Start stream
            setupRTSPStream()
            onLaunchDictation(deviceID, inputData, inputRoomName)
            setupIPSubmitButton()

            // Ensure roomName is non-null, else return
            val roomName = inputRoomName ?: return
            fetchWorkflow(roomName)

        }else{
            Toast.makeText(requireContext(), "Invalid input. Check IP Address and Room Name.", Toast.LENGTH_SHORT).show()
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigate(R.id.action_liveStreamingFragment_to_homeFragment)
        }

        binding.btnCloseStream.setOnClickListener {
            stopStream()
        }
    }

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

    // Receive emitted results from Python script
    private val onDataReceived = Emitter.Listener { args ->
        requireActivity().runOnUiThread {
            val data = args[0] as JSONObject

            // Receive data
            val sequences = data.getString("sequence")
            val predicted_class = data.getString("predicted_class")
            val message = data.getString("message")

            binding.tvPassValue.text = "Sequences: $sequences"
            binding.tvPassValue2.text = "Model Class: $predicted_class"
            binding.tvPassValue3.text = "Comment: $message"
        }
    }

    // Prepare texture view to host the camera
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

    private fun setupIPSubmitButton() {
        submitIPAndStartStream(inputData,inputRoomName)
    }

    private fun submitIPAndStartStream(inputData: String?,inputRoomName:String?) {
        val userInputIpAddress = inputData?.trim()
        val userRoom = inputRoomName?.trim()

        if (!userInputIpAddress.isNullOrEmpty() && !userRoom.isNullOrEmpty()) {
            streamUrl = "rtsp://$userInputIpAddress:8554/$deviceID"

            // POST to flask server
            sendPostRequest("post_device_info", deviceID, streamUrl,userInputIpAddress ?: "", userRoom ?: "")

            socket = IO.socket("http://$userInputIpAddress:5000")
            socket?.connect()

            // socket?.emit("join", JSONObject().put("room", userRoom))

            val data = JSONObject().apply {
                put("room", userRoom)
                put("camera_name", deviceID)  // ✅ Correct way to add multiple values
            }

            socket?.emit("join",data)

//            socket?.emit("join", JSONObject().put("room", userRoom))
            Toast.makeText(requireContext(), deviceID, Toast.LENGTH_SHORT).show()
            socket?.on(deviceID, onDataReceived)

            // Save streamUrl to SharedPreferences
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

        // Pass data
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

    private fun fetchWorkflow(roomName: String) {
        val fullURL = "$url/send_workflow/$roomName"

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(fullURL)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string() ?: "No Response"

                requireActivity().runOnUiThread {
                    try {
                        val jsonObject = JSONObject(responseData)
                        val workflowArray = jsonObject.getJSONArray("workflow") // Get workflow as JSONArray

                        val formattedWorkflow = StringBuilder()

                        for (i in 0 until workflowArray.length()) {
                            formattedWorkflow.append("${i + 1}. ${workflowArray.getString(i)}\n")
                        }

                        binding.tvWorkflowDesc.text = formattedWorkflow.toString().trim()

                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    //    private fun stopStream() {
//        if (rtspCamera.isStreaming) {
//            rtspCamera.stopStream()
//            rtspCamera.stopPreview()
//
//            val textureView = binding.textureView
//            textureView.surfaceTexture?.let {
//                textureView.surfaceTextureListener?.onSurfaceTextureDestroyed(
//                    it
//                )
//            }
//
//            Toast.makeText(requireContext(), "Stream stopped and exit room", Toast.LENGTH_SHORT).show()
//        }
//    }
    private fun stopStream() {
        val userRoom = inputRoomName?.trim()
        if (rtspCamera.isStreaming) {
            rtspCamera.stopStream()
            rtspCamera.stopPreview()

            val textureView = binding.textureView
            textureView.surfaceTexture?.let { surfaceTexture ->
                textureView.surfaceTextureListener?.onSurfaceTextureDestroyed(surfaceTexture)
            }

            // 🔥 Notify server that user ends the stream (leave room)
            socket?.let {
                if (it.connected()) {
                    val data = JSONObject().apply {
                        put("room", userRoom)
                        put("camera_name", deviceID)  // ✅ Correct way to add multiple values
                    }

                    socket?.emit("leave", data)
                    Log.d("Socket", "Leaving room with data: $data")
                }
            }

            Toast.makeText(requireContext(), "Stream stopped and exited room", Toast.LENGTH_SHORT)
                .show()
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
        socket?.disconnect()
        socket?.off(deviceID, onDataReceived)
        socket?.off(Socket.EVENT_CONNECT)
        socket?.off(Socket.EVENT_DISCONNECT)
        socket?.off(Socket.EVENT_CONNECT_ERROR)
//        socket?.let {
//            if (it.connected()) {
//                it.disconnect()
//                it.off("data", onDataReceived)
//            }
//       }
    }

    // Real Wear
    fun onLaunchDictation(deviceID: String?, inputData: String?, inputRoomName: String?) {
        val intent = Intent(MainActivity.ACTION_DICTATION).apply {
            if (deviceID != null) {
                putExtra("deviceID", deviceID)
            }
            if (inputData != null) {
                putExtra("text", inputData)
            }
            if (inputRoomName != null) {
                putExtra("text", inputRoomName)
            }
        }
        try {
            startActivityForResult(intent, MainActivity.DICTATION_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "RealWear Dictation Service not found", Toast.LENGTH_SHORT).show()
        }
    }
}