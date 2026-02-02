package com.example.fakecallapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callerName = intent.getStringExtra("caller_name") ?: "Unknown"
        val callerPhone = intent.getStringExtra("caller_phone") ?: "" // <--- GET IT

        val activityIntent = Intent(context, FakeCallActivity::class.java).apply {
            putExtra("caller_name", callerName)
            putExtra("caller_phone", callerPhone) // <--- PASS IT ALONG
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

        // --- 2. THE FIX: Attempt to Launch Activity Directly ---
        // This makes it pop up immediately if the "Display Over Other Apps" permission is granted
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // If for some reason direct launch fails, the notification below acts as a backup
            e.printStackTrace()
        }

        // --- 3. Create Notification (Backup & Lock Screen handling) ---
        // We still need this for the Lock Screen to wake up properly on some devices

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fake_call_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            importance = NotificationManager.IMPORTANCE_HIGH
            setSound(null, null) // Silent notification, because the Activity will play the ringtone
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX) // MAX priority is critical
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true) // Makes it harder to swipe away accidentally
            .build()

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(123, notification)
        }
    }
}