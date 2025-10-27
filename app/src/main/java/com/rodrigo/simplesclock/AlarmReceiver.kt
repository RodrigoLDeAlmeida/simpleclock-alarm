package com.rodrigo.simplesclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val musicUriString = intent?.getStringExtra("music_uri")

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("music_uri", musicUriString)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
