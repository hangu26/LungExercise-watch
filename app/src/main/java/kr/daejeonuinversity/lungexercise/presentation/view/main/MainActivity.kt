/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package kr.daejeonuinversity.lungexercise.presentation.view.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.daejeonuinversity.lungexercise.presentation.HeartRateService
import kr.daejeonuinversity.lungexercise.presentation.StepCounterService
import java.nio.ByteBuffer
import java.util.Calendar

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private var batteryDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {
            WearApp()
        }

        // Wearable 메시지 리스너 등록
        Wearable.getMessageClient(this).addListener(this)

        // 배터리 최적화 예외
        requestBatteryOptimizationException()

        // 화면 스케줄
        scheduleScreenWakeBeforeInterval()

        // 권한 요청
        if (!hasHeartRatePermissions()) requestHeartRatePermissions()
        if (!hasStepPermission()) requestStepPermission()
    }

    override fun onResume() {
        super.onResume()

        showBatteryOptimizationDialog()

        // 포그라운드에서 안전하게 서비스 시작
        if (hasHeartRatePermissions()) {
            Handler(Looper.getMainLooper()).postDelayed({
                startHeartRateService()
            }, 500L)
        }

        if (hasStepPermission()) {
            Handler(Looper.getMainLooper()).postDelayed({
                startStepCounterService()
            }, 500L)
        }
    }

    private fun startHeartRateService() {
        val intent = Intent(this, HeartRateService::class.java)
        startForegroundService(intent)
    }

    private fun startStepCounterService() {
        val intent = Intent(this, StepCounterService::class.java)
        startForegroundService(intent)
    }

    private fun hasHeartRatePermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }

        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun hasStepPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestStepPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1001)
        }
    }

    private fun requestHeartRatePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Handler(Looper.getMainLooper()).postDelayed({
                startHeartRateService()
                startStepCounterService()
            }, 500L)
        } else {
            Toast.makeText(this, "권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationException() {
        val packageName = packageName
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            Log.d("BatteryOptimization", "배터리 최적화 예외 요청을 보냈습니다.")
        } else {
            Log.d("BatteryOptimization", "배터리 최적화 예외가 이미 적용되었습니다.")
        }
    }

    @SuppressLint("BatteryLife")
    private fun showBatteryOptimizationDialog() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val dontShowAgain = prefs.getBoolean("dont_show_battery_dialog", false)

        if (!pm.isIgnoringBatteryOptimizations(packageName) && !batteryDialogShown && !dontShowAgain) {
            batteryDialogShown = true

            AlertDialog.Builder(this)
                .setTitle("배터리 최적화 예외 필요")
                .setMessage(
                    "앱이 백그라운드에서 정상 작동하려면 배터리 최적화 예외가 필요합니다.\n\n" +
                            "설정 → 배터리 → 절전 상태 앱 → 해당 앱 선택 → 최적화 해제"
                )
                .setPositiveButton("확인", null)
                .setNeutralButton("다시 보지 않기") { _, _ ->
                    prefs.edit().putBoolean("dont_show_battery_dialog", true).apply()
                }
                .show()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            Log.d("BatteryOptimization", if (pm.isIgnoringBatteryOptimizations(packageName))
                "배터리 최적화 예외 성공" else "배터리 최적화 예외 거부")
        }
    }

    private fun scheduleScreenWakeBeforeInterval() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val now = Calendar.getInstance()
                val nextInterval = Calendar.getInstance().apply {
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    val minute = (now.get(Calendar.MINUTE) / 30 + 1) * 30
                    set(Calendar.MINUTE, minute % 60)
                    if (minute >= 60) add(Calendar.HOUR_OF_DAY, 1)
                }
                val wakeTime = nextInterval.timeInMillis - 10_000
                val delayMs = wakeTime - System.currentTimeMillis()
                if (delayMs > 0) delay(delayMs)

                wakeScreenBriefly(2000L)
            }
        }
    }

    private fun wakeScreenBriefly(durationMs: Long = 1000L) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "StepCounter::TempWakeLock"
        )
        wakeLock.acquire(durationMs)
        Handler(Looper.getMainLooper()).postDelayed({
            if (wakeLock.isHeld) wakeLock.release()
        }, durationMs)
    }

    @Composable
    fun WearApp() {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Wear OS 앱 실행됨!",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/start_heart_rate_service" -> handleStartHeartRate(messageEvent)
            "/stop_step_count" -> handleStopStepCount()
            "/reset_step_count" -> HeartRateService.resetStepCountExternally()
            "/heart_rate_warning" -> triggerVibration()
        }
    }

    private fun handleStartHeartRate(messageEvent: MessageEvent) {
        val buffer = ByteBuffer.wrap(messageEvent.data)
        val exerciseTime = buffer.long

        // 화면 켜기
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "HeartRateService::WakeLockTag"
        )
        wakeLock.acquire(exerciseTime + 1000)

        // 진동
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }

        // HeartRateService 시작
        val intent = Intent(this, HeartRateService::class.java)
        intent.action = "START_STEP_COUNT"
        startService(intent)
    }

    private fun handleStopStepCount() {
        val intent = Intent(this, HeartRateService::class.java)
        intent.action = "STOP_STEP_COUNT"
        startService(intent)
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE))
        showToast("심박수 경고! 너무 높습니다.")
    }
}