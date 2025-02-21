package com.code4you.buche

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.gms.maps.model.CircleOptions
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.Style
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var isListening = false
    private lateinit var textView: TextView
    private lateinit var btnVoice: Button
    private val segnalazioni = mutableListOf<String>()
    private lateinit var mapView: MapView
    private lateinit var maplibreMap: MapLibreMap

    private val EMAIL_RECIPIENT = "marco.giardina@etik.com" // Cambia con l'email desiderata
    private val coordinateList = mutableListOf<Pair<Double, Double>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inizializza MapLibre
        MapLibre.getInstance(this)

        setContentView(R.layout.activity_main)

        // Ottieni il riferimento alla MapView
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

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

    override fun onMapReady(map: MapLibreMap) {
        maplibreMap = map

        // Impostare i limiti di zoom
        maplibreMap.setMinZoomPreference(10.0) // zoom minimo consentito
        maplibreMap.setMaxZoomPreference(20.0) // zoom massimo consentito

        // Impostare zoom con animazione
        maplibreMap.animateCamera(
            CameraUpdateFactory.zoomTo(20.0)
        )

        // Opzione 2: Maptiler streets (richiede API key gratuita)
        maplibreMap.setStyle(
            Style.Builder().fromUri("https://api.maptiler.com/maps/basic/style.json?key=PgttgVZBnbVXdswBdDvh")
        ) { style ->
            // La mappa è pronta e lo stile è stato caricato
        }

        // Imposta una posizione iniziale (ad esempio, Roma)
        val initialPosition = LatLng(41.9028, 12.4964)
        maplibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPosition, 10.0))
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
        val latLng = LatLng(location.latitude, location.longitude)
        maplibreMap.cameraPosition?.let {
            maplibreMap.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(latLng, 18.0))
        }

        // Aggiungi un marker per la posizione corrente
        maplibreMap.clear()
        maplibreMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromResource(R.drawable.blue_marker))
        )
    }

    private fun startListeningLoop() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dì 'Buca' per segnalare o 'Chiudi' per terminare")
            // Disabilita i suoni di beep
            //putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            //putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
            //putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            // Disabilita il suono di avvio
            //putExtra(RecognizerIntent.EXTRA_SOUND_DONE, false)
            //putExtra(RecognizerIntent.EXTRA_SOUND_START, false)
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
            // Stampa di debug per vedere cosa viene riconosciuto
            Log.d("VoiceCommand", "Comando riconosciuto: $command")

            when {
                command.contains("buca", ignoreCase = true) -> {
                    saveBucaLocation()
                    return
                }
                command.contains("chiudi", ignoreCase = true) -> {

                    // Ferma il riconoscimento vocale
                    speechRecognizer.stopListening()
                    speechRecognizer.destroy()

                    // Procedi con la chiusura
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
                    val position = LatLng(latitude, longitude)

                    // Salva le coordinate nella lista
                    coordinateList.add(Pair(latitude, longitude))

                    val segnalazione = "Buca: $latitude, $longitude"
                    segnalazioni.add(segnalazione)
                    textView.text = segnalazioni.joinToString("\n")

                    // Aggiungi il punto nero sulla mappa
                    createSmallBlackDot(position)

                    // Aggiungi un marker per la buca
                    maplibreMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(latitude, longitude))
                            .icon(org.maplibre.android.annotations.IconFactory.getInstance(this).fromResource(R.drawable.red_marker))
                    )

                    Toast.makeText(this, "Buca segnalata!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Aggiungi questa funzione per creare un piccolo marker nero
    private fun createSmallBlackDot(position: LatLng) {
        // Crea un piccolo punto nero programmaticamente
        val size = 20 // dimensione in pixel
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size/2f, size/2f, size/2f, paint)

        // Crea l'icona dal bitmap
        val icon = IconFactory.getInstance(this).fromBitmap(bitmap)

        // Aggiungi il marker
        maplibreMap.addMarker(
            MarkerOptions()
                .position(position)
                .icon(icon)
        )
    }

    // Nuova funzione per preparare e inviare l'email
    private fun sendEmailWithCoordinates() {
        val timestamp = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(
            Date()
        )

        val emailSubject = "Segnalazione Buche - $timestamp"
        val emailBody = buildString {
            appendLine("Segnalazioni buche rilevate:")
            appendLine("Data: $timestamp")
            appendLine("\nElenco coordinate:")
            coordinateList.forEachIndexed { index, (lat, lon) ->
                appendLine("${index + 1}. Latitudine: $lat, Longitudine: $lon")
                appendLine("   Google Maps: https://www.google.com/maps?q=$lat,$lon")
                appendLine("")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, emailSubject)
            putExtra(Intent.EXTRA_TEXT, emailBody)
        }

        try {
            startActivity(Intent.createChooser(intent, "Invio email..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Nessuna app email installata", Toast.LENGTH_SHORT).show()
        }
    }

    // Modifica la funzione closeApp
    private fun closeApp() {
        if (coordinateList.isNotEmpty()) {

            // Invia l'email con le coordinate
            sendEmailWithCoordinates()

            // Aspetta un momento per permettere l'invio dell'email
            Handler(Looper.getMainLooper()).postDelayed({
                textView.text = "Segnalazioni registrate:\n" + segnalazioni.joinToString("\n")
                Toast.makeText(this, "Chiusura dell'app...", Toast.LENGTH_SHORT).show()

                // Assicurati che il bottone sia visibile
                btnVoice.visibility = android.view.View.VISIBLE

                // Invece di finishAffinity, resettiamo lo stato dell'app
                resetAppState()
                //finishAffinity()
            }, 1000)
        } else {
            btnVoice.visibility = android.view.View.VISIBLE
            resetAppState()
            //finishAffinity()
        }
    }

    // Nuova funzione per resettare lo stato dell'app
    private fun resetAppState() {
        // Pulisci le liste
        coordinateList.clear()
        segnalazioni.clear()

        // Pulisci la textView
        textView.text = ""

        // Pulisci i marker dalla mappa
        maplibreMap.clear()

        // Torna alla posizione iniziale sulla mappa
        val initialPosition = LatLng(41.9028, 12.4964)
        maplibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPosition, 10.0))

        Toast.makeText(this, "App resettata", Toast.LENGTH_SHORT).show()
    }

    private fun closeApp_() {
        textView.text = "Segnalazioni registrate:\n" + segnalazioni.joinToString("\n")
        Toast.makeText(this, "Chiusura dell'app...", Toast.LENGTH_SHORT).show()
        finishAffinity()
    }

    private fun Button.hide() {
        this.post { this.visibility = android.view.View.GONE }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}