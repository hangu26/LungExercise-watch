package kr.daejeonuinversity.lungexercise.presentation.util.base

import android.annotation.SuppressLint
import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kr.daejeonuinversity.lungexercise.presentation.view.main.MainActivity

class MessageReceiverService : WearableListenerService() {
    @SuppressLint("WearRecents")
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/launch_app") {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}