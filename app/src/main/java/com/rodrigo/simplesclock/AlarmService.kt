package com.rodrigo.simplesclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_STOP_ALARM = "com.rodrigo.simplesclock.STOP_ALARM"
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopSelf() // This will trigger onDestroy
            return START_NOT_STICKY
        }

        // **THE DEFINITIVE FIX:** Acquire a WakeLock that forces the screen on.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SimplesClock::AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)

        val musicUriString = intent?.getStringExtra("music_uri")

        // Start Music Playback
        if (musicUriString != null && musicUriString != "null") {
            try {
                val musicUri = Uri.parse(musicUriString)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, musicUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                // Fallback if music fails
            }
        }

        // Show Full-Screen Notification and Start Activity
        val fullScreenIntent = Intent(this, AlarmActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "ALARM_SERVICE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alarm Service", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Alarm is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        startForeground(1, notification)

        // Forcefully launch the activity as a backup.
        startActivity(fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
