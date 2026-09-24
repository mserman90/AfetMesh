package com.example.afetmesh.data.models

import kotlinx.serialization.Serializable

enum class PacketType {
    DISCOVERY_BEACON,  // Peer discovery heartbeat
    CHAT_TEXT,         // P2P or Mesh Text Message
    EMERGENCY_SOS,     // High Priority SOS Broadcast
    VOICE_NOTE,        // Voice message recording (Base64)
    AUDIO_STREAM,      // Real-time Push-to-Talk / Voice Call PCM frame
    VIDEO_FRAME,       // Off-grid P2P live video stream frame (JPEG)
    PHOTO_TRANSFER,    // Damage photo attachment
    ACK                // Message delivery confirmation
}

object SosStatus {
    const val SAFE = "Güvendeyim"
    const val INJURED = "Yaralıyım"
    const val TRAPPED = "Enkaz Altındayım"
    const val NEED_HELP = "Acil Yardım Lazım"
    const val NEED_MEDICAL = "Tıbbi Destek Lazım"
    const val NEED_WATER_FOOD = "Su / Yiyecek Lazım"

    val ALL = listOf(SAFE, INJURED, TRAPPED, NEED_HELP, NEED_MEDICAL, NEED_WATER_FOOD)
}

@Serializable
data class MeshPacket(
    val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String = "*", // "*" = Broadcast to all
    val type: PacketType,
    val payload: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Int = 5, // Mesh hop count limit
    val hops: Int = 0,
    val senderBattery: Int = -1,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sosStatus: String? = null
)

data class PeerNode(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 8889,
    val lastSeen: Long = System.currentTimeMillis(),
    val battery: Int = -1,
    val isDirect: Boolean = true,
    val hops: Int = 1,
    val sosStatus: String? = null
)
