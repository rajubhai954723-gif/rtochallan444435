package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNotEmpty()) {
                val sender = messages[0].originatingAddress ?: "Unknown"
                val bodyText = messages.joinToString("") { it.messageBody ?: "" }
                
                val pendingResult = goAsync()
                val db = AppDatabase.getInstance(context)
                val repository = SmsRepository(db.ruleDao(), db.logDao())
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repository.forwardSms(sender, bodyText)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Failed to forward incoming SMS from $sender", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
