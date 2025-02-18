package com.code4you.buche

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.AsyncTask
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.osmdroid.config.Configuration
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

import org.osmdroid.views.MapView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    //private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isListening = false
    private lateinit var textView: TextView
    private val segnalazioni = mutableListOf<String>()

    // OSM
    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestPermissions()

        textView = findViewById(R.id.textView)
        val btnVoice = findViewById<Button>(R.id.btnVoice)

        btnVoice.setOnClickListener {
            btnVoice.hide() // Nasconde il pulsante
            startListeningLoop()
        }

        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val mapController = map.controller
        mapController.setZoom(18.0)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        updateLocation()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        ActivityCompat.requestPermissions(this, permissions, 200)
    }

    private fun startListeningLoop() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dì 'Buca' per segnalare o 'Chiudi buca' per chiudere")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    Log.d("RICONOSCIMENTO", "Parole riconosciute: $it")
                    processCommand(it)
                }
                startListeningLoop() // Continua ad ascoltare
            }

            override fun onError(error: Int) {
                Log.e("RICONOSCIMENTO", "Errore riconoscimento: $error")
                startListeningLoop() // Riavvia ascolto
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
            val lowerCommand = command.lowercase(Locale.ROOT)
            when {
                lowerCommand.contains("buca") -> {
                    saveBucaLocation()
                    return
                }
                lowerCommand.contains("chiudi buca") -> {
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
                    Toast.makeText(this, "Buca segnalata!", Toast.LENGTH_SHORT).show()
                    SendLocationTask(latitude, longitude).execute()
                }
            }
        }
    }

    private fun closeApp() {
        textView.text = "Segnalazioni registrate:\n" + segnalazioni.joinToString("\n")
        Toast.makeText(this, "Chiusura dell'app...", Toast.LENGTH_SHORT).show()
        finishAffinity() // Chiude l'app
    }

    private class SendLocationTask(private val latitude: Double, private val longitude: Double) : AsyncTask<Void, Void, String>() {
        override fun doInBackground(vararg params: Void?): String {
            val url = URL("http://192.168.1.1:3000/receiveLocation")
            val jsonParam = JSONObject().apply {
                put("latitude", latitude)
                put("longitude", longitude)
            }

            return try {
                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    OutputStreamWriter(outputStream).use { it.write(jsonParam.toString()) }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        "Posizione inviata con successo!"
                    } else {
                        "Errore nell'invio della posizione."
                    }
                }
            } catch (e: Exception) {
                "Errore: ${e.message}"
            }
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
        }
    }

    private fun Button.hide() {
        this.post { this.visibility = android.view.View.GONE }
    }

    private fun updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                map.controller.setCenter(geoPoint)
                map.controller.setZoom(18.0)
                val marker = Marker(map)
                marker.position = geoPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                map.overlays.add(marker)
                map.invalidate()
            }
        }
    }
}
