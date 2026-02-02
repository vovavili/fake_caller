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

    // --- 1. CROPPER LAUNCHER ---
    // Receives the FINAL cropped image from your custom CropActivity
    private val cropActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val croppedUri = result.data?.data
            if (croppedUri != null) {
                // Show it
                findViewById<android.widget.ImageView>(R.id.ivPreview).setImageURI(croppedUri)
                // Save it locally
                saveImageToInternalStorage(croppedUri)
            }
        }
    }

    // --- 2. CLASSIC GALLERY LAUNCHER (Re-implemented) ---
    // Receives the raw image from the Gallery, then immediately sends it to CropActivity
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val originalUri = result.data?.data
            if (originalUri != null) {
                // Immediately launch CropActivity with this image
                val intent = Intent(this, CropActivity::class.java)
                intent.data = originalUri
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                cropActivityLauncher.launch(intent)
            }
        }
    }

    // --- 3. CONTACT PICKER LAUNCHER ---
    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            populateContactDetails(contactUri)
        }
    }

    private val requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchContactPicker()
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        val ivPreview = findViewById<android.widget.ImageView>(R.id.ivPreview)
        val btnPickContact = findViewById<ImageButton>(R.id.btnPickContact)
        val btnSchedule = findViewById<Button>(R.id.btnSchedule)
        val npDelay = findViewById<NumberPicker>(R.id.npDelay)

        npDelay.minValue = 0
        npDelay.maxValue = 120
        npDelay.value = 10
        npDelay.wrapSelectorWheel = false

        // --- 4. CLICK LISTENER: Use Standard Gallery Intent ---
        ivPreview.setOnClickListener {
            // ACTION_PICK is the "Classic" single-select intent you wanted
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        btnPickContact.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker()
            } else {
                requestContactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        }

        btnSchedule.setOnClickListener {
            val name = findViewById<EditText>(R.id.etCallerName).text.toString()
            val phone = findViewById<EditText>(R.id.etPhoneNumber).text.toString()
            val delaySeconds = npDelay.value.toLong()
            scheduleCall(name, phone, delaySeconds)
        }

        // Reset image on fresh launch
        val file = File(filesDir, "custom_avatar.jpg")
        if (file.exists()) file.delete()
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun populateContactDetails(uri: Uri) {
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                val photoUriIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val photoUriString = if (photoUriIndex >= 0) it.getString(photoUriIndex) else null

                findViewById<EditText>(R.id.etCallerName).setText(name)
                findViewById<EditText>(R.id.etPhoneNumber).setText(number)

                if (photoUriString != null) {
                    val photoUri = photoUriString.toUri()
                    // If from contacts, also let them crop it if they want, or just save it.
                    // For now, let's just save it directly to keep it simple.
                    findViewById<android.widget.ImageView>(R.id.ivPreview).setImageURI(photoUri)
                    saveImageToInternalStorage(photoUri)
                } else {
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
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + (delaySeconds * 1000),
            pendingIntent
        )
        Toast.makeText(this, "Call scheduled in $delaySeconds second${if (delaySeconds != 1L) "s" else ""}", Toast.LENGTH_SHORT).show()

    }
}