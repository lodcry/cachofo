package com.lucasbeats

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.lucasbeats.gps.GpsManager
import com.lucasbeats.mesh.MeshManager
import com.lucasbeats.sms.SmsSender
import com.lucasbeats.store.Storage

class TorkService : Service() {

    companion object {
        var instance: TorkService? = null
        const val CHANNEL_ID = "tork_gps"
        const val NOTIF_ID   = 1
        const val MAX_CHAT   = 200
    }

    // IMPORTANTE: qualquer objeto que use getSystemService/getSharedPreferences por baixo
    // dos panos (GpsManager, SmsSender, MeshManager) precisa ser "by lazy" — criado só na
    // primeira vez que for usado, depois que o Android já anexou o Context ao Service.
    val mesh   by lazy { MeshManager(this) }
    val gps    by lazy { GpsManager(this) }
    val sender by lazy { SmsSender(this) }

    val handler = Handler(Looper.getMainLooper())

    // Histórico de chat em memória, espelhado no disco via Storage — assim a tela
    // principal, o overlay, e o que já existia antes do app abrir mostram sempre a
    // mesma coisa, sincronizados.
    val chatHistory: MutableList<ChatMessage> = mutableListOf()

    var onLocationUpdate: ((Double, Double, Float) -> Unit)? = null
    var onMemberUpdate:   ((List<Member>) -> Unit)?           = null
    var onChatMessage:    ((ChatMessage) -> Unit)?            = null

    private var myPhone = ""
    private var myName  = ""
    private var myColor = 0xFF4eff9a.toInt()

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
        try { chatHistory.addAll(Storage.loadChat(this)) } catch (_: Exception) {}
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
        if (mesh.contacts.isEmpty()) return
        val payload = SmsProtocol.encodeLoc(myPhone, myName, myColor, myLat, myLng, myAcc)
        try { sender.send(mesh.contacts, payload) } catch (e: Exception) {
            android.util.Log.e("Tork/Service", "Erro ao enviar localização: ${e.message}")
        }
    }

    fun onSmsReceived(body: String) {
        when {
            body.startsWith(SmsProtocol.PREFIX_LOC) -> {
                val member = SmsProtocol.decodeLoc(body) ?: return
                if (member.phone == myPhone) return
                mesh.updateLocation(member)
                handler.post { onMemberUpdate?.invoke(mesh.members.values.toList()) }
            }
            body.startsWith(SmsProtocol.PREFIX_CHAT) -> {
                val msg = SmsProtocol.decodeChat(body) ?: return
                if (msg.phone == myPhone) return
                persistChat(msg)
                handler.post { onChatMessage?.invoke(msg) }
            }
        }
    }

    fun sendChat(text: String) {
        if (myPhone.isEmpty() || text.isBlank()) return
        val msgId = System.currentTimeMillis().toString()
        val msg = ChatMessage(msgId, myPhone, myName, myColor, text, System.currentTimeMillis())
        persistChat(msg)
        if (mesh.contacts.isNotEmpty()) {
            val payload = SmsProtocol.encodeChatChunk(myPhone, myName, myColor, msgId, text)
            try { sender.send(mesh.contacts, payload) } catch (e: Exception) {
                android.util.Log.e("Tork/Service", "Erro ao enviar chat: ${e.message}")
            }
        }
        handler.post { onChatMessage?.invoke(msg) }
    }

    private fun persistChat(msg: ChatMessage) {
        chatHistory.add(msg)
        if (chatHistory.size > MAX_CHAT) chatHistory.removeAt(0)
        try { Storage.saveChat(this, chatHistory) } catch (_: Exception) {}
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
