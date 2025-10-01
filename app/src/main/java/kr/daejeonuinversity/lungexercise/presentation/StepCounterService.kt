package kr.daejeonuinversity.lungexercise.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import kr.daejeonuinversity.lungexercise.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StepCounterService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var baselineSteps: Int = 0

    private val stepIntervals = mutableListOf<StepInterval>()
    private val INTERVAL_MS = 30 * 60 * 1000L // 30분 단위

    data class StepInterval(
        val startTime: Long,
        var steps: Int,
        var finalized: Boolean = false // 구간 최종 여부
    )

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // 이전 저장 데이터 복원
        loadIntervals()
        baselineSteps = loadBaseline()

        // 앱 시작 시 저장된 구간 전송 및 로그
        stepIntervals.forEach {
            sendIntervalToPhone(it)
            logInterval(it)
        }

        createNotificationChannel()
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        saveIntervals()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val totalSteps = event.values[0].toInt()
        val todaySteps = totalSteps - baselineSteps
        val now = System.currentTimeMillis()
        val intervalStart = getIntervalStart(now)

        val previousTotal = stepIntervals.sumOf { it.steps }
        val currentInterval = stepIntervals.find { it.startTime == intervalStart }

        if (currentInterval != null) {
            // 실시간 업데이트
            currentInterval.steps = todaySteps - (previousTotal - currentInterval.steps)
        } else {
            // 새 구간이 시작되면 이전 구간 최종 처리
            val lastInterval = stepIntervals.lastOrNull()
            lastInterval?.let {
                it.finalized = true
                saveIntervalToPrefs(it)
                sendIntervalToPhone(it)
            }

            val newInterval = StepInterval(intervalStart, todaySteps - previousTotal)
            stepIntervals.add(newInterval)
        }

        // 현재 구간도 실시간 전송
        sendIntervalToPhone(currentInterval ?: stepIntervals.last())
        logInterval(currentInterval ?: stepIntervals.last())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    /** 30분 단위 시작 시간 계산 */
    private fun getIntervalStart(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE) / 30 * 30)
        return cal.timeInMillis
    }

    /** SharedPreferences에 날짜별 구간 저장 */
    private fun saveIntervalToPrefs(interval: StepInterval) {
        val prefs = getSharedPreferences("step_intervals", Context.MODE_PRIVATE)
        val json = prefs.getString("step_intervals", "{}")
        val mapType = object : TypeToken<MutableMap<String, MutableList<StepInterval>>>() {}.type
        val allData: MutableMap<String, MutableList<StepInterval>> = Gson().fromJson(json, mapType) ?: mutableMapOf()

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(interval.startTime)
        if (!allData.containsKey(dateKey)) allData[dateKey] = mutableListOf()

        // 동일 구간이면 업데이트
        val existingIndex = allData[dateKey]?.indexOfFirst { it.startTime == interval.startTime } ?: -1
        if (existingIndex >= 0) {
            allData[dateKey]?.set(existingIndex, interval)
        } else {
            allData[dateKey]?.add(interval)
        }

        prefs.edit().putString("step_intervals", Gson().toJson(allData)).apply()
    }

    /** 휴대폰 앱으로 구간 전송 */
    private fun sendIntervalToPhone(interval: StepInterval) {
        val dataMap = PutDataMapRequest.create("/step_interval").apply {
            dataMap.putInt("steps", interval.steps)
            dataMap.putLong("intervalStart", interval.startTime)
        }
        Wearable.getDataClient(this)
            .putDataItem(dataMap.asPutDataRequest().setUrgent())
    }

    /** 로그 출력 */
    private fun logInterval(interval: StepInterval) {
        val cal = Calendar.getInstance().apply { timeInMillis = interval.startTime }
        val startHour = cal.get(Calendar.HOUR_OF_DAY)
        val startMinute = cal.get(Calendar.MINUTE)
        val endMinute = if (startMinute == 0) 30 else 0
        val endHour = if (startMinute == 0) startHour else startHour + 1
        val startStr = String.format("%02d:%02d", startHour, startMinute)
        val endStr = String.format("%02d:%02d", endHour, endMinute)
        Log.d("StepCounter", "📥 $startStr 부터 $endStr 까지 걸음수: ${interval.steps}")
    }

    /** 모든 구간 저장 */
    private fun saveIntervals() {
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("step_intervals", Gson().toJson(stepIntervals)).apply()
    }

    /** 앱 시작 시 저장된 구간 불러오기 */
    private fun loadIntervals() {
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("step_prefs", null)
        stepIntervals.clear()
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<StepInterval>>() {}.type
            stepIntervals.addAll(Gson().fromJson(json, type))
        }
    }

    /** baselineSteps 불러오기 */
    private fun loadBaseline(): Int {
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("baseline_steps", 0)
    }

    /** 포그라운드 알림 */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "step_counter_channel",
            "걸음 수 측정 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "백그라운드에서 걸음 수를 측정합니다." }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, "step_counter_channel")
            .setContentTitle("걸음 수 측정 중")
            .setContentText("백그라운드에서 걸음 수를 기록합니다.")
            .setSmallIcon(R.drawable.splash_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }
}



