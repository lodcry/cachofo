package com.lucasbeats

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.lucasbeats.gps.GpsManager
import com.lucasbeats.mesh.MeshManager
import com.lucasbeats.sms.SmsSender

class TorkService : Service() {

    companion object {
        var instance: TorkService? = null
        const val CHANNEL_ID = "tork_gps"
        const val NOTIF_ID   = 1
    }

    val mesh = MeshManager()

    // IMPORTANTE: gps e sender precisam ser "lazy" — eles usam getSystemService()
    // internamente, e isso só pode ser chamado com segurança DEPOIS que o Android
    // termina de "anexar" o Context ao Service (attachBaseContext). Se forem
    // criados direto no construtor da classe (val x = Classe(this)), o Context
    // ainda está incompleto e getSystemService() retorna null → NullPointerException
    // no exato momento em que o serviço é instanciado. Com "by lazy", a criação só
    // acontece na primeira vez que "gps" ou "sender" forem usados de verdade —
    // e isso só ocorre dentro do onCreate(), quando o Context já está pronto.
    val gps    by lazy { GpsManager(this) }
    val sender by lazy { SmsSender(this) }

    val handler = Handler(Looper.getMainLooper())

    // Callbacks pra UI
    var onLocationUpdate: ((Double, Double, Float) -> Unit)? = null
    var onMemberUpdate:   ((List<Member>) -> Unit)?           = null
    var onChatMessage:    ((ChatMessage) -> Unit)?            = null

    private var myPhone = ""
    private var myName  = ""
    private var myColor = 0xFF4eff9a.toInt()

    // Intervalo de broadcast GPS via SMS (5 segundos)
    private val GPS_INTERVAL = 5000L
    private var myLat = 0.0; private var myLng = 0.0; private var myAcc = 0f
    private val gpsRunnable = object : Runnable {
        override fun run() {
            broadcastLocation()
            handler.postDelayed(this, GPS_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate(); instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotif("🌿 Tork ativo"))
        startGps()
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        try { gps.stop() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        i?.let {
            myPhone = it.getStringExtra("phone") ?: myPhone
            myName  = it.getStringExtra("name")  ?: myName
            myColor = it.getIntExtra("color", myColor)
        }
        return START_STICKY
    }

    private fun startGps() {
        try {
            gps.start { lat, lng, acc ->
                myLat = lat; myLng = lng; myAcc = acc
                onLocationUpdate?.invoke(lat, lng, acc)
                // Prune membros offline
                mesh.pruneStale()
                onMemberUpdate?.invoke(mesh.members.values.toList())
            }
        } catch (e: Exception) {
            android.util.Log.e("Tork/Service", "Erro ao iniciar GPS: ${e.message}")
        }
        handler.postDelayed(gpsRunnable, GPS_INTERVAL)
    }

    private fun broadcastLocation() {
        if (myPhone.isEmpty() || myLat == 0.0) return
        if (mesh.contacts.isEmpty()) return // evita chamada de SmsManager sem destinatários
        val payload = SmsProtocol.encodeLoc(myPhone, myName, myColor, myLat, myLng, myAcc)
        try { sender.send(mesh.contacts, payload) } catch (e: Exception) {
            android.util.Log.e("Tork/Service", "Erro ao enviar localização: ${e.message}")
        }
    }

    // Chamado pelo SmsReceiver
    fun onSmsReceived(body: String) {
        when {
            body.startsWith(SmsProtocol.PREFIX_LOC) -> {
                val member = SmsProtocol.decodeLoc(body) ?: return
                if (member.phone == myPhone) return // ignora próprio
                mesh.updateLocation(member)
                handler.post { onMemberUpdate?.invoke(mesh.members.values.toList()) }
            }
            body.startsWith(SmsProtocol.PREFIX_CHAT) -> {
                val msg = SmsProtocol.decodeChat(body) ?: return
                if (msg.phone == myPhone) return
                handler.post { onChatMessage?.invoke(msg) }
            }
        }
    }

    fun sendChat(text: String) {
        if (myPhone.isEmpty() || text.isBlank()) return
        val msgId = System.currentTimeMillis().toString()
        val payload = SmsProtocol.encodeChatChunk(myPhone, myName, myColor, msgId, text)
        if (mesh.contacts.isNotEmpty()) {
            try { sender.send(mesh.contacts, payload) } catch (e: Exception) {
                android.util.Log.e("Tork/Service", "Erro ao enviar chat: ${e.message}")
            }
        }
        // Exibe localmente também
        val msg = ChatMessage(msgId, myPhone, myName, myColor, text, System.currentTimeMillis())
        handler.post { onChatMessage?.invoke(msg) }
    }

    fun setProfile(phone: String, name: String, color: Int) {
        myPhone = phone; myName = name; myColor = color
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Tork GPS", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Rastreamento GPS ativo"; setSound(null, null) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tork").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
