package com.lucasbeats.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.lucasbeats.SmsProtocol
import com.lucasbeats.TorkService

// Recebe SMS do sistema e repassa pro TorkService se for payload Tork
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        // Agrupa partes de SMS multipart pelo endereço de origem
        val grouped = mutableMapOf<String, StringBuilder>()
        messages.forEach { sms ->
            grouped.getOrPut(sms.displayOriginatingAddress) { StringBuilder() }
                .append(sms.messageBody)
        }
        grouped.forEach { (_, body) ->
            val txt = body.toString()
            if (txt.startsWith(SmsProtocol.PREFIX_LOC) || txt.startsWith(SmsProtocol.PREFIX_CHAT)) {
                TorkService.instance?.onSmsReceived(txt)
            }
        }
    }
}
