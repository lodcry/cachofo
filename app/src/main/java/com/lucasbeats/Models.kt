package com.lucasbeats

// Representa um membro do grupo
data class Member(
    val phone: String,          // número de telefone = ID único
    val name: String,           // nome escolhido
    val color: Int,             // cor ARGB
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var accuracy: Float = 0f,
    var lastSeen: Long = 0L,    // timestamp ms
    var trail: MutableList<TrailPoint> = mutableListOf()
)

data class TrailPoint(val lat: Double, val lng: Double, val ts: Long)

data class ChatMessage(
    val id: String,
    val phone: String,          // remetente
    val name: String,
    val color: Int,
    val text: String,
    val ts: Long
)

// Payload SMS — prefixo TORK: para identificar mensagens do app
// Formato localização : TORK:L|phone|name|colorHex|lat|lng|acc|ts
// Formato chat        : TORK:C|phone|name|colorHex|msgId|texto
object SmsProtocol {
    const val PREFIX_LOC  = "TORK:L|"
    const val PREFIX_CHAT = "TORK:C|"
    const val MAX_SMS_LEN = 160

    fun encodeLoc(phone: String, name: String, color: Int, lat: Double, lng: Double, acc: Float): String {
        val hex = String.format("%06X", color and 0xFFFFFF)
        val latS = "%.5f".format(lat)
        val lngS = "%.5f".format(lng)
        val accS = acc.toInt().toString()
        val ts   = (System.currentTimeMillis() / 1000).toString() // segundos, economiza chars
        return "${PREFIX_LOC}${phone}|${name}|${hex}|${latS}|${lngS}|${accS}|${ts}"
    }

    fun encodeChatChunk(phone: String, name: String, color: Int, msgId: String, text: String): String {
        val hex = String.format("%06X", color and 0xFFFFFF)
        // trunca texto pra caber num SMS
        val header = "${PREFIX_CHAT}${phone}|${name}|${hex}|${msgId}|"
        val maxText = MAX_SMS_LEN - header.length
        return header + text.take(maxText)
    }

    fun decodeLoc(body: String): Member? {
        return try {
            val p = body.removePrefix(PREFIX_LOC).split("|")
            if (p.size < 7) return null
            val color = android.graphics.Color.parseColor("#${p[2]}") or 0xFF000000.toInt()
            Member(
                phone    = p[0],
                name     = p[1],
                color    = color,
                lat      = p[3].toDouble(),
                lng      = p[4].toDouble(),
                accuracy = p[5].toFloat(),
                lastSeen = p[6].toLong() * 1000L
            )
        } catch (_: Exception) { null }
    }

    fun decodeChat(body: String): ChatMessage? {
        return try {
            val p = body.removePrefix(PREFIX_CHAT).split("|", limit = 5)
            if (p.size < 5) return null
            val color = android.graphics.Color.parseColor("#${p[2]}") or 0xFF000000.toInt()
            ChatMessage(
                id    = p[3],
                phone = p[0],
                name  = p[1],
                color = color,
                text  = p[4],
                ts    = System.currentTimeMillis()
            )
        } catch (_: Exception) { null }
    }
}
