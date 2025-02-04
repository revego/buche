package com.code4you.buche

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.ResponseBody
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class MainActivity : AppCompatActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnVoice: Button
    private var latitude: Double? = null
    private var longitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnVoice = findViewById(R.id.btnVoice)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestPermissions()

        btnVoice.setOnClickListener { startVoiceRecognition() }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dì 'Segnala buca'")
        }
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (result?.get(0)?.contains("Segnala buca", ignoreCase = true) == true) {
                startBucaReport()
            }
        }
    }


    private fun startBucaReport() {
        getCurrentLocation()
        Toast.makeText(this, "Registrazione della buca avviata", Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    Toast.makeText(this, "Posizione: $latitude, $longitude", Toast.LENGTH_SHORT).show()
                    sendAudioReport("Segnalazione buca", latitude!!, longitude!!)
                }
            }
        }
    }

    private fun sendAudioReport(audioText: String, lat: Double, lon: Double) {
        // Serializzazione e invio alla API
        val report = BucaReport(audioText, lat, lon)
        val retrofit = Retrofit.Builder()
            //.baseUrl("https://tuoserver.com/")
            .baseUrl("http://192.168.1.10:3000")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ReportApi::class.java)
        api.sendReport(report).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Toast.makeText(this@MainActivity, "Segnalazione inviata", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Errore: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun requestPermissions() {
        // Controlla se i permessi sono già stati concessi
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            // Se i permessi non sono concessi, chiedili
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permessi concessi, puoi procedere
                getCurrentLocation()
                startVoiceRecognition()
            } else {
                // Permessi negati
                Toast.makeText(this, "I permessi sono necessari per continuare", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class BucaReport(val audioText: String, val latitude: Double, val longitude: Double)

interface ReportApi {
    @POST("segnaBuca")
    fun sendReport(@Body report: BucaReport): Call<ResponseBody>
}


