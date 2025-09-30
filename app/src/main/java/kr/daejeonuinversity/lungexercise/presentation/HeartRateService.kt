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

class HeartRateService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var stepSensor: Sensor? = null
    private var spo2Sensor: Sensor? = null
    private var triggerEventListener: TriggerEventListener? = null
    private var stepCount = 0
    private lateinit var wakeLock: PowerManager.WakeLock
    private var initialStepCount: Int? = null

    companion object {
        private var externalReset: Boolean = false

        fun resetStepCountExternally() {
            externalReset = true
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER )
//        spo2Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_O)

        if (heartRateSensor == null) {
            Log.e("HeartRateService", "❌ 심박수 센서 없음!")
        } else {
            Log.d("HeartRateService", "✅ 심박수 센서 찾음: ${heartRateSensor!!.name}")
        }

        if (stepSensor == null) {
            Log.e("HeartRateService", "❌ 걸음걸이 센서 없음!")
        } else {
            Log.d("HeartRateService", "✅ 걸음걸이 센서 찾음: ${stepSensor!!.name}")
        }

        createNotificationChannel()
        startForegroundService()
    }

    private fun startForegroundService() {
        val notification = if (Build.VERSION.SDK_INT >= 34) {
            Notification.Builder(this, "heart_rate_channel")
                .setContentTitle("심박수 측정 중")
                .setContentText("현재 심박수를 지속적으로 측정합니다.")
                .setSmallIcon(R.drawable.splash_icon)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFAULT)
                .build()
        } else {
            NotificationCompat.Builder(this, "heart_rate_channel")
                .setContentTitle("심박수 측정 중")
                .setContentText("현재 심박수를 지속적으로 측정합니다.")
                .setSmallIcon(R.drawable.splash_icon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "heart_rate_channel",
            "심박수 측정 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "심박수를 지속적으로 측정하는 서비스입니다."
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    heartRateSensor?.let { sensor ->
    Log.d("HeartRateService", "📌 심박수 센서 등록 시도")

    val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    Log.d("HeartRateService", "📌 SensorEventListener 등록 성공 여부: $success")

    if (!success) {
    Log.e("HeartRateService", "❌ 센서 등록 실패: 권한 부족 또는 백그라운드 제약 가능성")
    }

    } ?: Log.e("HeartRateService", "❌ 심박수 센서가 없어서 등록 실패!")

    return START_STICKY
    }
     **/

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireWakeLock()

        registerSensor(heartRateSensor, "심박수")
        registerSensor(spo2Sensor, "산소포화도")
        registerSensor(stepSensor, "걸음 수")
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
//            PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
            PowerManager.PARTIAL_WAKE_LOCK,
            "HeartRateService::WakeLockTag"
        )
        if (!wakeLock.isHeld) {
            wakeLock.acquire(30 * 60 * 1000L /*10분*/)  // 필요시 시간 조절
            Log.d("HeartRateService", "💡 WakeLock 획득됨")
        }
    }


    private fun registerSensor(sensor: Sensor?, label: String) {
        sensor?.let {
            val success =
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d("Sensor", "📌 $label 센서 등록 성공 여부: $success")
        } ?: Log.w("Sensor", "❌ $label 센서를 사용할 수 없습니다.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                event.values.firstOrNull()?.let { heartRate ->
                    Log.d("HeartRateService", "❤️ 심박수: $heartRate")
                    sendDataToPhone(heartRate)
                }
            }
            /**
            Sensor.TYPE_STEP_COUNTER -> {
            event.values.firstOrNull()?.let { steps ->
            Log.d("HeartRateService", "👟 걸음 수: ${steps.toInt()}")
            sendStepToPhone(steps.toInt())
            }
            }
             **/
//            Sensor.TYPE_STEP_COUNTER -> {
//                event.values.firstOrNull()?.toInt()?.let { totalSteps ->
//                    if (initialStepCount == null) initialStepCount = totalSteps
//                    val sessionSteps = totalSteps - (initialStepCount ?: 0)
//                    Log.d("HeartRateService", "👟 걸음 수 전송: $sessionSteps (누적 $totalSteps)")
//                    sendStepToPhone(sessionSteps)
//                }
//            }

            Sensor.TYPE_STEP_COUNTER -> {
                event.values.firstOrNull()?.toInt()?.let { totalSteps ->
                    // 초기값 없거나 외부 리셋 요청 시 초기값 갱신
                    if (initialStepCount == null || externalReset) {
                        initialStepCount = totalSteps
                        externalReset = false
                        Log.d("HeartRateService", "👟 세션 걸음 수 초기화됨, 초기값: $initialStepCount")
                    }

                    // 세션 걸음수 계산
                    val sessionSteps = totalSteps - (initialStepCount ?: 0)
                    Log.d("HeartRateService", "👟 세션 걸음 수 전송: $sessionSteps (누적 $totalSteps)")
                    sendStepToPhone(sessionSteps)
                }
            }

        }
    }

    private fun sendStepToPhone(steps: Int) {
        val putDataStep = PutDataMapRequest.create("/step_count").apply {
            dataMap.putInt("step_count", steps)
            dataMap.putLong("timestamp", System.currentTimeMillis()) // 중요!
        }
        val stepRequest = putDataStep.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(stepRequest)
            .addOnSuccessListener {
                Log.d("HeartRate", "📤 걸음수 전송 성공: $steps")
            }
            .addOnFailureListener {
                Log.e("HeartRate", "❌ 전송 실패", it)
            }
    }


    private fun sendDataToPhone(heartRate: Float) {
        val putDataReq = PutDataMapRequest.create("/heart_rate").apply {
            dataMap.putFloat("heart_rate", heartRate)
            dataMap.putLong("timestamp", System.currentTimeMillis()) // 중요!
        }
        val request = putDataReq.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
            .addOnSuccessListener {
                Log.d("HeartRate", "📤 심박수 전송 성공: $heartRate")
            }
            .addOnFailureListener {
                Log.e("HeartRate", "❌ 전송 실패", it)
            }
    }


    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        triggerEventListener?.let { sensorManager.cancelTriggerSensor(it, heartRateSensor) }

        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
            Log.d("HeartRateService", "🔋 WakeLock 해제됨")
        }

        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null
}

