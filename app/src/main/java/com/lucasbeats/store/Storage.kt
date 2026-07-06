package com.lucasbeats.store

import android.content.Context
import com.lucasbeats.ChatMessage
import com.lucasbeats.Member
import org.json.JSONArray
import org.json.JSONObject

// Tudo que precisa sobreviver a reinício do app/serviço mora aqui, em SharedPreferences.
// Antes: contatos, membros e mensagens viviam só em memória (RAM) — fechou o app, morreu.
// Agora: toda escrita relevante (addContact, updateLocation, sendChat) também grava aqui,
// e toda inicialização (MeshManager, TorkService) lê daqui antes de começar a rodar.
object Storage {
    private const val PREFS       = "tork_store"
    private const val KEY_CONTACTS = "contacts"
    private const val KEY_MEMBERS  = "members"
    private const val KEY_CHAT     = "chat_history"
    private const val MAX_CHAT     = 200

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Contatos (lista de números do grupo) ──────────────────────────────────
    fun loadContacts(ctx: Context): MutableSet<String> {
        val raw = prefs(ctx).getString(KEY_CONTACTS, null) ?: return mutableSetOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        } catch (_: Exception) { mutableSetOf() }
    }

    fun saveContacts(ctx: Context, contacts: Set<String>) {
        val arr = JSONArray(); contacts.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    fun saveContactName(ctx: Context, phone: String, name: String) {
        prefs(ctx).edit().putString("contact_name_$phone", name).apply()
    }

    fun loadContactName(ctx: Context, phone: String): String? =
        prefs(ctx).getString("contact_name_$phone", null)

    // ── Membros (última posição conhecida de cada um) ─────────────────────────
    fun loadMembers(ctx: Context): MutableMap<String, Member> {
        val raw = prefs(ctx).getString(KEY_MEMBERS, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Member>()
            obj.keys().forEach { phone ->
                val m = obj.getJSONObject(phone)
                map[phone] = Member(
                    phone    = phone,
                    name     = m.optString("name", phone),
                    color    = m.optInt("color", 0xFF4eff9a.toInt()),
                    lat      = m.optDouble("lat", 0.0),
                    lng      = m.optDouble("lng", 0.0),
                    accuracy = m.optDouble("accuracy", 0.0).toFloat(),
                    lastSeen = m.optLong("lastSeen", 0L)
                )
            }
            map
        } catch (_: Exception) { mutableMapOf() }
    }

    fun saveMembers(ctx: Context, members: Map<String, Member>) {
        val obj = JSONObject()
        members.forEach { (phone, m) ->
            obj.put(phone, JSONObject().apply {
                put("name", m.name); put("color", m.color)
                put("lat", m.lat); put("lng", m.lng)
                put("accuracy", m.accuracy.toDouble()); put("lastSeen", m.lastSeen)
            })
        }
        prefs(ctx).edit().putString(KEY_MEMBERS, obj.toString()).apply()
    }

    // ── Histórico de chat ──────────────────────────────────────────────────────
    fun loadChat(ctx: Context): MutableList<ChatMessage> {
        val raw = prefs(ctx).getString(KEY_CHAT, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ChatMessage(
                    id    = o.optString("id"),
                    phone = o.optString("phone"),
                    name  = o.optString("name"),
                    color = o.optInt("color"),
                    text  = o.optString("text"),
                    ts    = o.optLong("ts")
                )
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    fun saveChat(ctx: Context, messages: List<ChatMessage>) {
        val trimmed = if (messages.size > MAX_CHAT) messages.takeLast(MAX_CHAT) else messages
        val arr = JSONArray()
        trimmed.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("phone", m.phone); put("name", m.name)
                put("color", m.color); put("text", m.text); put("ts", m.ts)
            })
        }
        prefs(ctx).edit().putString(KEY_CHAT, arr.toString()).apply()
    }
}
