package com.lucasbeats.mesh

import android.content.Context
import com.lucasbeats.Member
import com.lucasbeats.TrailPoint
import com.lucasbeats.store.Storage

// Mantém o estado de todos os membros do grupo — agora com persistência real via
// SharedPreferences (Storage). Antes tudo ficava só em memória e sumia ao reabrir
// o app: esse era o motivo de "adiciono contato mas não aparece salvo".
class MeshManager(private val ctx: Context) {

    private val _members: MutableMap<String, Member> = Storage.loadMembers(ctx)
    val members: Map<String, Member> get() = _members

    private val _contacts: MutableSet<String> = Storage.loadContacts(ctx)
    val contacts: List<String> get() = _contacts.toList()

    fun addContact(phone: String, name: String? = null) {
        _contacts.add(phone)
        Storage.saveContacts(ctx, _contacts)
        if (!name.isNullOrEmpty()) Storage.saveContactName(ctx, phone, name)
    }

    fun removeContact(phone: String) {
        _contacts.remove(phone)
        _members.remove(phone)
        Storage.saveContacts(ctx, _contacts)
        Storage.saveMembers(ctx, _members)
    }

    fun updateLocation(member: Member) {
        val existing = _members[member.phone]
        if (existing != null) {
            existing.lat = member.lat
            existing.lng = member.lng
            existing.accuracy = member.accuracy
            existing.lastSeen = member.lastSeen
            existing.trail.add(TrailPoint(member.lat, member.lng, member.lastSeen))
            if (existing.trail.size > 500) existing.trail.removeAt(0)
        } else {
            member.trail.add(TrailPoint(member.lat, member.lng, member.lastSeen))
            _members[member.phone] = member
            _contacts.add(member.phone)
            Storage.saveContacts(ctx, _contacts)
        }
        Storage.saveMembers(ctx, _members)
    }

    // Mantido só por compatibilidade — o "online/offline" agora é calculado na UI
    // comparando lastSeen com o horário atual. Apagar aqui destruiria o último local
    // conhecido só porque a pessoa ficou 10min offline, o que não queremos.
    fun pruneStale(maxAgeMs: Long = 10 * 60 * 1000L) { /* no-op intencional */ }
}
