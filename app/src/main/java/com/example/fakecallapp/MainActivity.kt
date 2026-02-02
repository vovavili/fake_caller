package com.example.fakecallapp

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    // --- 1. CONTACT PICKER LAUNCHER ---
    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            populateContactDetails(contactUri)
        }
    }

    // --- 2. PERMISSION LAUNCHER ---
    private val requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchContactPicker()
        } else {
            Toast.makeText(this, "Permission denied. Cannot pick contact.", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                findViewById<android.widget.ImageView>(R.id.ivPreview).setImageURI(uri)
                saveImageToInternalStorage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions() // Standard permissions (Notif, Overlay, Alarm)

        // Setup UI
        val ivPreview = findViewById<android.widget.ImageView>(R.id.ivPreview)
        val btnPickContact = findViewById<ImageButton>(R.id.btnPickContact) // <--- NEW BUTTON
        val btnSchedule = findViewById<Button>(R.id.btnSchedule)
        val npDelay = findViewById<NumberPicker>(R.id.npDelay)

        // Setup Spinner
        npDelay.minValue = 0
        npDelay.maxValue = 120
        npDelay.value = 10
        npDelay.wrapSelectorWheel = false

        // 1. IMAGE CLICK (Manual Photo)
        ivPreview.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        // 2. CONTACT BUTTON CLICK
        btnPickContact.setOnClickListener {
            // Check if we have permission. If not, ask. If yes, open picker.
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker()
            } else {
                requestContactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        }

        // 3. SCHEDULE CLICK
        btnSchedule.setOnClickListener {
            val name = findViewById<EditText>(R.id.etCallerName).text.toString()
            val phone = findViewById<EditText>(R.id.etPhoneNumber).text.toString()
            val delaySeconds = npDelay.value.toLong()
            scheduleCall(name, phone, delaySeconds)
        }

        // Cleanup old image on start
        val file = File(filesDir, "custom_avatar.jpg")
        if (file.exists()) file.delete()
    }

    private fun launchContactPicker() {
        // We strictly want phones, so we pick from CommonDataKinds.Phone
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun populateContactDetails(uri: Uri) {
        // Query the content provider for the specific contact clicked
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                // 1. Get Name
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"

                // 2. Get Number
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val number = if (numberIndex >= 0) it.getString(numberIndex) else ""

                // 3. Get Photo URI (Thumbnail or High Res)
                val photoUriIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val photoUriString = if (photoUriIndex >= 0) it.getString(photoUriIndex) else null

                // --- UPDATE UI ---
                findViewById<EditText>(R.id.etCallerName).setText(name)
                findViewById<EditText>(R.id.etPhoneNumber).setText(number)

                // --- HANDLE PHOTO ---
                if (photoUriString != null) {
                    val photoUri = photoUriString.toUri()
                    findViewById<android.widget.ImageView>(R.id.ivPreview).setImageURI(photoUri)
                    saveImageToInternalStorage(photoUri) // Save it for the fake call later
                } else {
                    // Reset to default if contact has no photo
                    findViewById<android.widget.ImageView>(R.id.ivPreview).setImageResource(R.drawable.ic_avatar_default)
                    val file = File(filesDir, "custom_avatar.jpg")
                    if (file.exists()) file.delete()
                }
            }
        }
    }

    private fun saveImageToInternalStorage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val file = File(filesDir, "custom_avatar.jpg")
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkPermissions() {
        // ... (Keep your existing checks for Notif, Overlay, Alarm) ...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply { data = "package:$packageName".toUri() })
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
        }
    }

    private fun scheduleCall(name: String, phone: String, delaySeconds: Long) {
        // ... (Keep your existing schedule logic exactly as it was) ...
        if (delaySeconds == 0L) {
            val intent = Intent(this, FakeCallActivity::class.java).apply {
                putExtra("caller_name", name)
                putExtra("caller_phone", phone)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            return
        }

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }

        val intent = Intent(this, CallReceiver::class.java).apply {
            putExtra("caller_name", name)
            putExtra("caller_phone", phone)
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (delaySeconds * 1000), pendingIntent)
        Toast.makeText(this, "Call scheduled in $delaySeconds second${if (delaySeconds != 1L) "s" else ""}.", Toast.LENGTH_LONG).show()

    }
}