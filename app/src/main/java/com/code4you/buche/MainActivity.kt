package com.code4you.buche

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var isListening = false
    private lateinit var textView: TextView
    private lateinit var btnVoice: Button
    private val segnalazioni = mutableListOf<String>()
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        textView = findViewById(R.id.textView)
        btnVoice = findViewById(R.id.btnVoice)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissions()
        setupLocationUpdates()

        btnVoice.setOnClickListener {
            btnVoice.hide()
            startListeningLoop()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        ActivityCompat.requestPermissions(this, permissions, 200)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permessi necessari non concessi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { updateMap(it) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun updateMap(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        map.controller.setCenter(geoPoint)
        map.controller.setZoom(18.0)

        val marker = Marker(map).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = resources.getDrawable(R.drawable.blue_marker, null)
        }
        map.overlays.clear()
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun startListeningLoop() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dì 'Buca' per segnalare o 'Chiudi buca' per chiudere")
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { processCommand(it) }
                startListeningLoop()
            }

            override fun onError(error: Int) {
                startListeningLoop()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun processCommand(commands: List<String>) {
        for (command in commands) {
            when {
                command.contains("buca", ignoreCase = true) -> {
                    saveBucaLocation()
                    return
                }
                command.contains("chiudi buca", ignoreCase = true) -> {
                    closeApp()
                    return
                }
            }
        }
    }

    private fun saveBucaLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    val segnalazione = "Buca: $latitude, $longitude"
                    segnalazioni.add(segnalazione)
                    textView.text = segnalazioni.joinToString("\n")

                    val marker = Marker(map).apply {
                        position = GeoPoint(latitude, longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = resources.getDrawable(R.drawable.red_marker, null)
                    }
                    map.overlays.add(marker)
                    map.invalidate()

                    Toast.makeText(this, "Buca segnalata!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun closeApp() {
        textView.text = "Segnalazioni registrate:\n" + segnalazioni.joinToString("\n")
        Toast.makeText(this, "Chiusura dell'app...", Toast.LENGTH_SHORT).show()
        finishAffinity()
    }

    private fun Button.hide() {
        this.post { this.visibility = android.view.View.GONE }
    }
}
