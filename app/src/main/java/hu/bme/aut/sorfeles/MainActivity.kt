@file:Suppress("DEPRECATION")

package hu.bme.aut.sorfeles

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit


private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class MainActivity : AppCompatActivity() {

    private var vibration_enabled = true

    private var fileName: String = ""

    private var popupRunning = false
    private var running = false

    private var pauseOffset : Long = 0

    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }

    private fun startRecording() {
        Log.d("btn", "Start recording")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e("Audio", "prepare() failed")
            }
            start()
        }
    }

    private fun stopRecording() {
        Log.d("btn", "Stop recording")
        recorder?.apply {
            try {
                stop()
                release()
            }
            catch(e: RuntimeException){
                Log.d("Mediarecorder", "Failed to stop or release!")
            }
        }
        recorder = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        button.setOnClickListener {
            if (running){
                pauseOffset =   SystemClock.elapsedRealtime() - chronometer.base
                chronometer.stop()
                button.text = getString(R.string.start_text)
                running = false
            }
            else {
                chronometer.base = SystemClock.elapsedRealtime() - pauseOffset
                chronometer.start()
                button.text = getString(R.string.stop_text)
                running = true
            }
        }

        reset.setOnClickListener{
            chronometer.base = SystemClock.elapsedRealtime()
            pauseOffset = 0
        }

        chronometer.setOnChronometerTickListener {
            val elapsedMillis: Long =
                SystemClock.elapsedRealtime() - it.base
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis)

            when (seconds % 60L){
                55L-> {
                    if (vibration_enabled){
                        val vibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            vibrator.vibrate(500)
                        }
                    }

                    player = MediaPlayer().apply {
                        try {
                            setDataSource(fileName)
                            prepare()
                            start()
                        } catch (e: IOException) {
                            Log.e("Audio player", "prepare() failed")
                        }
                    }

                    showPopupWindow(LinearLayout(this),R.layout.popup_window_toast)
                }
                0L-> {
                    showPopupWindow(LinearLayout(this), R.layout.popup_window_drink)
                }
            }
        }

        record.setOnTouchListener { v, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {startRecording(); v.performClick()}
                MotionEvent.ACTION_UP -> {stopRecording()}
            }

            v?.onTouchEvent(event) ?: true
        }

        fileName = "${externalCacheDir?.absolutePath}/toast.3gp"

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

    }

    private fun showPopupWindow(location_view: View, layout_id: Int) {
        if (popupRunning) return

        popupRunning = true
        // inflate the layout of the popup window
        val inflater =
            getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView: View = inflater.inflate(layout_id, null)

        // create the popup window
        val width = LinearLayout.LayoutParams.MATCH_PARENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = false // lets taps outside the popup also dismiss it
        val popupWindow = PopupWindow(popupView, width, height, focusable)

        val handler = Handler()
        val runnable = Runnable {
            if (popupWindow.isShowing) {
                popupWindow.dismiss()
                popupRunning = false
            }
        }

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(location_view, Gravity.TOP, 0, 0)

        // dismiss the popup window when touched
        handler.postDelayed(runnable,1500)
    }
}
