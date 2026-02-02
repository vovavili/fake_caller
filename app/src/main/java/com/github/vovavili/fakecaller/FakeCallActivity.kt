package com.github.vovavili.fakecaller

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FakeCallActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Remove the Notification from the top bar (Clean up)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(123) // Cancel the notification ID we set in the Receiver

        // 2. Setup Window for Lock Screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // 3. THE FIX FOR THE BLACK BAR (Notch/Cutout Support)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        setContentView(R.layout.activity_fake_call)
        hideSystemUI()

        val ivAvatar = findViewById<android.widget.ImageView>(R.id.ivAvatar)

        // LOGIC: Check for custom image
        val file = java.io.File(filesDir, "custom_avatar.jpg")
        if (file.exists()) {
            // Load custom image
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            ivAvatar.setImageBitmap(bitmap)
            // Fix scale to make it look like a circle or fill nicely
            ivAvatar.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        } else {
            // Load Default "Unknown" Icon
            ivAvatar.setImageResource(R.drawable.ic_avatar_default)
            ivAvatar.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        // Setup basic UI
        val callerName = intent.getStringExtra("caller_name")
        val callerPhone = intent.getStringExtra("caller_phone") // <--- GET IT

        findViewById<TextView>(R.id.tvCallerName).text = callerName
        playRingtone()

        // --- BUTTONS ---
        val layoutIncoming = findViewById<android.view.View>(R.id.layoutIncoming)
        val btnHangUp = findViewById<Button>(R.id.btnHangUp)
        val tvStatus = findViewById<TextView>(R.id.tvCallStatus)
        if (!callerPhone.isNullOrEmpty()) {
            tvStatus.text = "Mobile $callerPhone"
        } else {
            tvStatus.text = "Mobile"
        }
        val chronometer = findViewById<android.widget.Chronometer>(R.id.chronometer)

        // 1. DECLINE Button
        findViewById<Button>(R.id.btnDecline).setOnClickListener {
            stopRingtone()
            finishAndRemoveTask() // Quits the app, returning to lock screen
        }

        // 2. ANSWER Button
        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            stopRingtone()

            // Hide "Incoming" buttons
            layoutIncoming.visibility = android.view.View.GONE

            // Show "Hang Up" button
            btnHangUp.visibility = android.view.View.VISIBLE

            // --- THE FIX ---
            // Instead of setting text to "00:00", we HIDE this view completely.
            tvStatus.visibility = android.view.View.GONE

            // Show and start the real Timer (Chronometer)
            chronometer.visibility = android.view.View.VISIBLE
            chronometer.base = android.os.SystemClock.elapsedRealtime()
            chronometer.start()
        }

        // 3. HANG UP Button (Used after answering)
        btnHangUp.setOnClickListener {
            chronometer.stop()
            finishAndRemoveTask() // Quits the app, returning to lock screen
        }
    }
    private fun playRingtone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, notification)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                isLooping = true // Repeat the ringtone
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone() // Ensure sound stops if user swipes app away or system kills it
    }

    private fun hideSystemUI() {
        // Defines the flags to hide the status bar, nav bar, and make it immersive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // For older Android versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
}