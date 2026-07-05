package com.lucasbeats.mesh

import com.lucasbeats.Member
import com.lucasbeats.TrailPoint

// Mantém o estado de todos os membros do grupo em memória
// Identificador único = número de telefone
class MeshManager {

    private val _members = mutableMapOf<String, Member>()
    val members: Map<String, Member> get() = _members

    // Contatos do grupo (números pra enviar SMS)
    private val _contacts = mutableSetOf<String>()
    val contacts: List<String> get() = _contacts.toList()

    fun addContact(phone: String) { _contacts.add(phone) }
    fun removeContact(phone: String) { _contacts.remove(phone); _members.remove(phone) }

    fun updateLocation(member: Member) {
        val existing = _members[member.phone]
        if (existing != null) {
            existing.lat      = member.lat
            existing.lng      = member.lng
            existing.accuracy = member.accuracy
            existing.lastSeen = member.lastSeen
            // Adiciona ponto na trilha
            existing.trail.add(TrailPoint(member.lat, member.lng, member.lastSeen))
            if (existing.trail.size > 500) existing.trail.removeAt(0) // limite de trilha
        } else {
            member.trail.add(TrailPoint(member.lat, member.lng, member.lastSeen))
            _members[member.phone] = member
            _contacts.add(member.phone) // auto-adiciona quem manda localização
        }
    }

    fun pruneStale(maxAgeMs: Long = 10 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        _members.entries.removeAll { it.value.lastSeen < cutoff }
    }
}
