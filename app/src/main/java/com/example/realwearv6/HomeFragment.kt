package com.example.realwearv6

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.example.realwearv6.databinding.FragmentHomeBinding
import java.sql.Connection

// new added to connect with Flask
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.Button;
import android.widget.EditText;
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

import android.content.Context
import android.util.Log
import okhttp3.*
import java.util.*


class HomeFragment : Fragment() {
    private lateinit var binding: FragmentHomeBinding
    private var userInputRoomName: String? = null
    private var ipAddressOnly: String = ""

    // Variables for connecting Flask
    private lateinit var textFieldMessage: EditText
    private lateinit var buttonSendPost: Button
    private lateinit var buttonSendGet: Button
    private lateinit var textViewResponse: TextView
    private var url: String = ""
    private val POST = "POST"
    private val GET = "GET"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater,container, false)
        url = loadConfig("server_ip")
        ipAddressOnly = getIpAddressOnly(url)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load available rooms
        sendRequest(GET,"get_available_rooms")

        binding.imageBtnRefreshRoom.setOnClickListener{
            sendRequest(GET,"get_available_rooms")
        }

        binding.lvRoomName?.setOnItemClickListener { parent, view, position, id ->
            userInputRoomName = parent.getItemAtPosition(position).toString().trim()

            binding.tvRoom.text = "Selected Room: $userInputRoomName"

            Toast.makeText(requireContext(), "Selected Room: $userInputRoomName", Toast.LENGTH_SHORT).show()
        }

        binding.btnIPSubmit.setOnClickListener{
//            val userInputIpAddress = binding.userInputIPAddress.text.toString().trim()
//            val userInputRoomName = binding.spinnerRoomName.selectedItem.toString().trim()

            if(!ipAddressOnly.isNullOrEmpty() && !userInputRoomName.isNullOrEmpty()){
                val bundle = Bundle()
                bundle.putString("userInputIpAddress", ipAddressOnly)
                bundle.putString("userInputRoomName",userInputRoomName)
//                bundle.putInt("targetId", binding.userInputIPAddress.id)

//                val targetId = binding.userInputIPAddress.id
//                Log.d("TargetID", "Target ID: $targetId")

                val currentDestination = findNavController().currentDestination
                if (currentDestination?.id != R.id.liveStreamingFragment) {
                    findNavController().navigate(R.id.action_homeFragment_to_liveStreamingFragment, bundle)

                } else {
                    Toast.makeText(requireContext(), "You are already in the LiveStreamingFragment", Toast.LENGTH_SHORT).show()
                }

            } else{
                Toast.makeText(requireContext(), "Please provide both a valid IP Address and Room Name.", Toast.LENGTH_SHORT).show()
            }
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

    private fun getIpAddressOnly(fullUrl: String): String {
        return try {
            val uri = java.net.URI(fullUrl)
            uri.host ?: "default-ip"
        } catch (e: Exception) {
            Log.e("ConfigError", "Invalid URL format: ${e.message}")
            "default-ip"
        }
    }

    private fun sendRequest(type: String, method: String) {
        val fullURL = "$url/$method"

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

            // Receive response
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string() ?: "No Response"

                requireActivity().runOnUiThread {
                    // Split into individual room IDs (assuming the response is space-separated)
                    val roomIdList = responseData.split(",")

                    // Create ArrayAdapter for the ListView
                    val adapter = ArrayAdapter(requireContext(), R.layout.list_item_room, R.id.tvRoomItem, roomIdList)

                    // Set the adapter to the ListView
                    binding.lvRoomName?.adapter = adapter
                }
            }
        })
    }

}