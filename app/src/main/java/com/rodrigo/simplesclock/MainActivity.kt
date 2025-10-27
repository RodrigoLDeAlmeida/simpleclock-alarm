package com.rodrigo.simplesclock

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_POST_NOTIFICATIONS = 1
    private val REQUEST_CODE_READ_MEDIA_AUDIO = 3

    private lateinit var timePicker: TimePicker
    private lateinit var selectedMusicText: TextView
    private lateinit var nextAlarmText: TextView
    private lateinit var dayToggles: List<ToggleButton>
    private var selectedMusicUri: Uri? = null
    private lateinit var sharedPreferences: SharedPreferences

    private val chooseMusicLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    selectedMusicUri = uri
                    val ringtone = RingtoneManager.getRingtone(this, uri)
                    selectedMusicText.text = ringtone.getTitle(this)
                } catch (e: SecurityException) {
                    Toast.makeText(this, "Could not get permission for the selected music.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)

        timePicker = findViewById(R.id.timePicker)
        selectedMusicText = findViewById(R.id.selectedMusicText)
        nextAlarmText = findViewById(R.id.nextAlarmText)
        dayToggles = listOf(
            findViewById(R.id.sundayToggle), findViewById(R.id.mondayToggle), findViewById(R.id.tuesdayToggle),
            findViewById(R.id.wednesdayToggle), findViewById(R.id.thursdayToggle), findViewById(R.id.fridayToggle),
            findViewById(R.id.saturdayToggle)
        )

        findViewById<Button>(R.id.chooseMusicButton).setOnClickListener { checkMusicPermissionAndOpenChooser() }
        findViewById<Button>(R.id.setAlarmButton).setOnClickListener { checkPermissionsAndSetAlarm() }

        loadSettings()
    }

    @Suppress("DEPRECATION")
    private fun loadSettings() {
        val hour = sharedPreferences.getInt("hour", Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        val minute = sharedPreferences.getInt("minute", Calendar.getInstance().get(Calendar.MINUTE))
        if (Build.VERSION.SDK_INT >= 23) {
            timePicker.hour = hour
            timePicker.minute = minute
        } else {
            timePicker.currentHour = hour
            timePicker.currentMinute = minute
        }

        val musicUriString = sharedPreferences.getString("music_uri", null)
        if (musicUriString != null && musicUriString != "null") {
            try {
                selectedMusicUri = Uri.parse(musicUriString)
                val ringtone = RingtoneManager.getRingtone(this, selectedMusicUri)
                selectedMusicText.text = ringtone.getTitle(this)
            } catch (e: Exception) {
                selectedMusicText.text = "No music selected"
                selectedMusicUri = null
            }
        }

        val selectedDaysJson = sharedPreferences.getString("selected_days", null)
        if (selectedDaysJson != null) {
            val type = object : TypeToken<List<Int>>() {}.type
            val selectedDays: List<Int> = Gson().fromJson(selectedDaysJson, type)
            for (day in selectedDays) {
                dayToggles.getOrNull(day - 1)?.isChecked = true
            }
        }
        updateNextAlarmText(false) // Don't set a new alarm on load
    }

    @Suppress("DEPRECATION")
    private fun saveSettings() {
        with(sharedPreferences.edit()) {
            val hour = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
            val minute = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute
            putInt("hour", hour)
            putInt("minute", minute)
            putString("music_uri", selectedMusicUri?.toString())

            val selectedDays = getSelectedDays()
            val selectedDaysJson = Gson().toJson(selectedDays)
            putString("selected_days", selectedDaysJson)
            apply()
        }
    }

    private fun checkMusicPermissionAndOpenChooser() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), REQUEST_CODE_READ_MEDIA_AUDIO)
        } else {
            openMusicChooser()
        }
    }

    private fun openMusicChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "audio/*"
        chooseMusicLauncher.launch(intent)
    }

    private fun checkPermissionsAndSetAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                Toast.makeText(this, "Please grant permission to schedule exact alarms.", Toast.LENGTH_LONG).show()
                return
            }
        }
        proceedToSetAlarm()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            return
        }
        when (requestCode) {
            REQUEST_CODE_POST_NOTIFICATIONS -> checkPermissionsAndSetAlarm()
            REQUEST_CODE_READ_MEDIA_AUDIO -> openMusicChooser()
        }
    }

    private fun getSelectedDays(): List<Int> {
        val days = mutableListOf<Int>()
        dayToggles.forEachIndexed { index, toggleButton ->
            if (toggleButton.isChecked) days.add(index + 1) // Sunday is 1, Monday is 2, etc.
        }
        return days
    }

    private fun proceedToSetAlarm() {
        saveSettings()
        updateNextAlarmText(true) // Set an alarm when proceeding
    }

    private fun updateNextAlarmText(setAlarm: Boolean) {
        val (nextAlarmTime, toastMessage) = calculateNextAlarm()
        if (nextAlarmTime > 0) {
            if (setAlarm) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(this, AlarmReceiver::class.java).apply {
                    putExtra("music_uri", selectedMusicUri.toString())
                }
                val alarmPendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                // This intent opens MainActivity when the user taps the system alarm icon
                val showActivityIntent = Intent(this, MainActivity::class.java)
                val showActivityPendingIntent = PendingIntent.getActivity(this, 1, showActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, showActivityPendingIntent)

                // Use setAlarmClock for privileged wake-up behavior
                alarmManager.setAlarmClock(alarmClockInfo, alarmPendingIntent)
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
            }

            val sdf = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())
            nextAlarmText.text = "Next alarm: ${sdf.format(Date(nextAlarmTime))}"
        } else {
            nextAlarmText.text = "No alarm set"
        }
    }

    @Suppress("DEPRECATION")
    fun calculateNextAlarm(): Pair<Long, String> {
        val hour = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
        val minute = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute
        val selectedDays = getSelectedDays()
        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (selectedDays.isEmpty()) {
            if (alarmTime.before(now)) {
                alarmTime.add(Calendar.DAY_OF_MONTH, 1)
            }
            return Pair(alarmTime.timeInMillis, "Alarm set for tomorrow.")
        } else {
            for (i in 0..7) {
                val potentialAlarm = alarmTime.clone() as Calendar
                potentialAlarm.add(Calendar.DAY_OF_YEAR, i)
                if (selectedDays.contains(potentialAlarm.get(Calendar.DAY_OF_WEEK)) && potentialAlarm.after(now)) {
                    return Pair(potentialAlarm.timeInMillis, "Alarm set.")
                }
            }
        }
        return Pair(0L, "Could not set alarm.")
    }
}
