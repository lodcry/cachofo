package com.lucasbeats.sms

import android.content.Context
import android.telephony.SmsManager

class SmsSender(private val ctx: Context) {

    // Envia pra todos os contatos do grupo
    fun send(targets: List<String>, body: String) {
        val mgr = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        targets.forEach { phone ->
            try {
                // SmsManager divide automaticamente se > 160 chars
                val parts = mgr.divideMessage(body)
                if (parts.size == 1) mgr.sendTextMessage(phone, null, body, null, null)
                else mgr.sendMultipartTextMessage(phone, null, parts, null, null)
            } catch (e: Exception) {
                android.util.Log.e("Tork/SMS", "Erro ao enviar pra $phone: ${e.message}")
            }
        }
    }
}
