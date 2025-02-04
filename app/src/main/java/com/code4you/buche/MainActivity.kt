package com.code4you.buche

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val recorder = AudioRecorder()
    private lateinit var btnVoice: Button
    private lateinit var btnRecord: Button
    private var latitude: Double? = null
    private var longitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnVoice = findViewById(R.id.btnVoice)
        btnRecord = findViewById(R.id.btnRecord)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestPermissions()

        btnVoice.setOnClickListener { startVoiceRecognition() }
        btnRecord.setOnClickListener { startRecording() }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
                ), 100)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permessi concessi!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permessi negati!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 200)
    }


    private fun requestPermissions_() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, 200)
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
                }
            }
        }
    }

    private fun startRecording() {
        recorder.startRecording()
        Handler(Looper.getMainLooper()).postDelayed({
            val filePath = recorder.stopRecording()
            Toast.makeText(this, "Audio salvato in $filePath", Toast.LENGTH_SHORT).show()

            if (latitude != null && longitude != null) {
                uploadReport(filePath, latitude!!, longitude!!)
            } else {
                Toast.makeText(this, "Posizione non disponibile", Toast.LENGTH_SHORT).show()
            }
        }, 5000) // Registra per 5 secondi
    }

    private fun uploadReport(audioFilePath: String, lat: Double, lon: Double) {
        val file = File(audioFilePath)
        val requestBody = file.asRequestBody("audio/mp3".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Part.createFormData("audio", file.name, requestBody)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://tuoserver.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ReportApi::class.java)
        api.sendReport(multipartBody, lat, lon).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Toast.makeText(this@MainActivity, "Segnalazione inviata", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("ReportApi", "Errore nella segnalazione", t)
                Toast.makeText(this@MainActivity, "Errore: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

class AudioRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private val filePath = "${Environment.getExternalStorageDirectory().absolutePath}/buca_report.mp3"
    //private val filePath = "${context.getExternalFilesDir(null)?.absolutePath}/buca_report.mp3"


    fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(filePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            prepare()
            start()
        }
    }

    fun stopRecording(): String {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        return filePath
    }
}

interface ReportApi {
    @Multipart
    @POST("upload")
    fun sendReport(
        @Part audio: MultipartBody.Part,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Call<ResponseBody>
}

annotation class Multipart
