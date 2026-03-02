package kr.daejeonuinversity.lungexercise.presentation

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kr.daejeonuinversity.lungexercise.R
import kr.daejeonuinversity.lungexercise.presentation.util.util.WearSensorState

class HeartRateService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var stepSensor: Sensor? = null
    private var spo2Sensor: Sensor? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    // 센서 기준
    private var lastTotalSteps: Int? = null
    private var initialStepCount: Int? = null   // 현재 세션 시작 기준
    private var sessionStepOffset = 0           // stop 시점까지 누적된 세션 걸음수
    private var isStepCounting = true

    companion object {
        private var instance: HeartRateService? = null

        fun resetStepCountExternally() {
            instance?.let {
                it.sessionStepOffset = 0
                it.initialStepCount = null
                it.lastTotalSteps = null
                Log.d("HeartRateService", "👟 걸음 수 초기화 완료")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
//        spo2Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_0)

        createNotificationChannel()
        startForegroundServiceNotification()
    }

    /** 걸음수 측정 시작 */
    fun startStepCounting() {
        stepSensor?.let {
            registerSensor(it, "걸음 수")
            isStepCounting = true
            Log.d("HeartRateService", "👟 걸음 수 측정 재시작됨")
        }
    }

    /** 걸음수 측정 중지 */
    fun stopStepCounting() {
        stepSensor?.let {
            sensorManager.unregisterListener(this, it)
            initialStepCount?.let { initial ->
                lastTotalSteps?.let { total ->
                    sessionStepOffset += (total - initial)
                }
            }
            initialStepCount = null
            isStepCounting = false
            Log.d("HeartRateService", "👟 걸음 수 측정 중지됨, offset: $sessionStepOffset")
        }
    }

    /** 포그라운드 알림 시작 */
    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, "heart_rate_channel")
            .setContentTitle("심박수 측정 중")
            .setContentText("걸음 수 및 심박수를 기록합니다.")
            .setSmallIcon(R.drawable.splash_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "heart_rate_channel",
            "심박수 측정 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "걸음 수 및 심박수를 기록하는 서비스" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 센서 등록 */
    private fun registerSensor(sensor: Sensor?, label: String) {
        sensor?.let {
            val success = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d("Sensor", "📌 $label 센서 등록 성공 여부: $success")
        } ?: Log.w("Sensor", "❌ $label 센서를 사용할 수 없습니다.")
    }

    /** 서비스 시작 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "START_STEP_COUNT" -> startStepCounting()
                "STOP_STEP_COUNT" -> stopStepCounting()
                "/reset_step_count" -> resetStepCountExternally()
            }
        }

        acquireWakeLock()
        if (isStepCounting) registerSensor(stepSensor, "걸음 수")
        registerSensor(heartRateSensor, "심박수")
        registerSensor(spo2Sensor, "산소포화도")
        return START_STICKY
    }

    /** WakeLock 획득 */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartRateService::WakeLockTag")
        if (!wakeLock.isHeld) {
            wakeLock.acquire(30 * 60 * 1000L)
            Log.d("HeartRateService", "💡 WakeLock 획득됨")
        }
    }

    /** 센서 값 변화 처리 */
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                event.values.firstOrNull()?.let { heartRate ->
                    WearSensorState.heartRate = heartRate.toInt()
                    Log.d("HeartRateService", "❤️ 심박수: $heartRate")
                    sendHeartRateToPhone(heartRate)
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values.firstOrNull()?.toInt() ?: return
                lastTotalSteps = totalSteps

                if (initialStepCount == null) {
                    initialStepCount = totalSteps
                    Log.d("HeartRateService", "👟 초기 세션 걸음수 설정: $initialStepCount")
                }
                val sessionSteps = totalSteps - (initialStepCount ?: 0) + sessionStepOffset
                WearSensorState.stepCount = sessionSteps
                Log.d("HeartRateService", "👟 세션 걸음 수 전송: $sessionSteps (총 $totalSteps)")
                sendStepToPhone(sessionSteps)
            }
        }
    }

    /** 휴대폰 전송 */
    private fun sendStepToPhone(steps: Int) {
        val putData = PutDataMapRequest.create("/step_count").apply {
            dataMap.putInt("step_count", steps)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        Wearable.getDataClient(this).putDataItem(putData.asPutDataRequest().setUrgent())
    }

    private fun sendHeartRateToPhone(heartRate: Float) {
        val putData = PutDataMapRequest.create("/heart_rate").apply {
            dataMap.putFloat("heart_rate", heartRate)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        Wearable.getDataClient(this).putDataItem(putData.asPutDataRequest().setUrgent())
    }

    /** 서비스 종료 */
    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        instance = null
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
}






