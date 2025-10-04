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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.daejeonuinversity.lungexercise.R
import kr.daejeonuinversity.lungexercise.presentation.data.local.StepDatabase
import kr.daejeonuinversity.lungexercise.presentation.data.local.dao.StepDao
import kr.daejeonuinversity.lungexercise.presentation.data.local.entity.StepDataEntity
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StepCounterService : Service(), SensorEventListener {

    // -----------------------
    // Koin DI로 StepDao 주입
    private val stepDao: StepDao by inject()
    // -----------------------

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private var lastStepCount: Int? = null
    private var baselineSteps: Int = 0

    private val INTERVAL_MS = 30 * 60 * 1000L // 30분 단위
    private val stepMutex = Mutex()



    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // SharedPreferences 기반 baseline 로드
        baselineSteps = loadBaseline()

        // 앱 시작 시 기존 구간 전송
        sendAllStepsToPhone()
        registerPhoneRequestListener()

        createNotificationChannel()
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stepSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val totalFromSensor = event.values[0].toInt()

        // 초기 세팅
        if (lastStepCount == null) {
            lastStepCount = totalFromSensor
            if (baselineSteps == 0) {
                baselineSteps = totalFromSensor
                saveBaseline(baselineSteps)
            }
            return
        }

        val delta = totalFromSensor - lastStepCount!!
        lastStepCount = totalFromSensor

        if (delta < 0) {
            // ⚠️ 재부팅 등으로 센서값 리셋됨
            baselineSteps = totalFromSensor
            saveBaseline(baselineSteps)
            return
        }

        val now = System.currentTimeMillis()
        val intervalStart = getIntervalStart(now)
        val intervalEnd = intervalStart + INTERVAL_MS

        // -----------------------
        // StepInterval 생성
        val stepData = StepDataEntity(
            stepCount = delta,
            startTime = intervalStart,
            endTime = intervalEnd
        )

        // 1️⃣ Room DB에 insert or update
        CoroutineScope(Dispatchers.IO).launch {
            stepMutex.withLock {
                stepDao.upsertStep(stepData)
            }
        }


        saveIntervalToPrefs(intervalStart, intervalEnd, delta)
//        sendAllStepsToPhone()

        logInterval(intervalStart, intervalEnd, delta)
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

    /** 로그 출력 */
    private fun logInterval(start: Long, end: Long, steps: Int) {
        val calStart = Calendar.getInstance().apply { timeInMillis = start }
        val calEnd = Calendar.getInstance().apply { timeInMillis = end }

        val startStr = String.format(
            "%02d:%02d",
            calStart.get(Calendar.HOUR_OF_DAY),
            calStart.get(Calendar.MINUTE)
        )
        val endStr = String.format(
            "%02d:%02d",
            calEnd.get(Calendar.HOUR_OF_DAY),
            calEnd.get(Calendar.MINUTE)
        )
        Log.d("StepCounter", "📥 $startStr 부터 $endStr 까지 걸음수: $steps")
    }

    /** SharedPreferences baselineSteps 저장 */
    private fun saveBaseline(value: Int) {
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("baseline_steps", value).apply()
    }

    /** SharedPreferences baselineSteps 불러오기 */
    private fun loadBaseline(): Int {
        val prefs = getSharedPreferences("step_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("baseline_steps", 0)
    }

    /** SharedPreferences에 구간 저장 (기존 코드) */
    private fun saveIntervalToPrefs(start: Long, end: Long, steps: Int) {
        val prefs = getSharedPreferences("step_intervals", Context.MODE_PRIVATE)
        val json = prefs.getString("step_intervals", "{}")
        val mapType = object : TypeToken<MutableMap<String, MutableList<StepDataEntity>>>() {}.type
        val allData: MutableMap<String, MutableList<StepDataEntity>> =
            Gson().fromJson(json, mapType) ?: mutableMapOf()

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(start)
        if (!allData.containsKey(dateKey)) allData[dateKey] = mutableListOf()

        val newData = StepDataEntity(stepCount = steps, startTime = start, endTime = end)
        allData[dateKey]?.add(newData)

        prefs.edit().putString("step_intervals", Gson().toJson(allData)).apply()
    }

    /**
    private fun sendIntervalToPhone() {
    val dataMap = PutDataMapRequest.create("/step_interval").apply {
    dataMap.putInt("steps", interval.steps)
    dataMap.putLong("intervalStart", interval.startTime)
    }
    Wearable.getDataClient(this).putDataItem(dataMap.asPutDataRequest().setUrgent())
    }
     **/

    /** Room에 저장된 모든 구간을 휴대폰으로 전송 후 삭제 */

    private fun sendAllStepsToPhone() {
        CoroutineScope(Dispatchers.IO).launch {
            val allSteps = stepDao.getAllStepData()
            allSteps.forEach { step ->
                val dataMap = PutDataMapRequest.create("/step_interval").apply {
                    dataMap.putInt("steps", step.stepCount)
                    dataMap.putLong("intervalStart", step.startTime)
                    dataMap.putLong("intervalEnd", step.endTime)
                }

                Wearable.getDataClient(this@StepCounterService)
                    .putDataItem(dataMap.asPutDataRequest().setUrgent())
                    .addOnSuccessListener {
                        Log.d("HeartRateService", "전송 성공: ${step.startTime} ~ ${step.endTime}")
                    }
                    .addOnCompleteListener {
                        // 전송 완료 시 DB 삭제
                        CoroutineScope(Dispatchers.IO).launch {
//                            stepDao.deleteStepById(step.id)
                        }
                    }
            }
        }
    }

    private fun registerPhoneRequestListener() {
        Wearable.getMessageClient(this).addListener { messageEvent ->
            if (messageEvent.path == "/request_steps") {
                Log.d("HeartRateService", "휴대폰에서 데이터 요청 수신")

                // 1️⃣ 화면 켜기
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "HeartRateService::WakeLockTag"
                )
                wakeLock.acquire(2000) // 6초간 화면 켜기

                sendAllStepsToPhone()
            }
        }
    }

    /** 포그라운드 알림 채널 생성 */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "step_counter_channel",
            "걸음 수 측정 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "백그라운드에서 걸음 수를 측정합니다." }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 포그라운드 알림 시작 */
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





