package com.example.afetmesh.data.mesh

import android.util.Log
import com.example.afetmesh.data.models.MeshPacket
import com.example.afetmesh.data.models.PacketType
import org.json.JSONObject
import java.util.UUID

object InteropProtocolAdapter {

    private const val TAG = "InteropProtocolAdapter"

    /**
     * Attempts to parse raw JSON from external off-grid disaster applications
     * (Briar, Meshenger, com.afetiletisim.deprem, Bridgeify, etc.)
     * and convert it into a standardized AfetMesh MeshPacket.
     */
    fun parseExternalPacket(rawJson: String): MeshPacket? {
        return try {
            val json = JSONObject(rawJson)

            // Extract Sender ID (Briar, Meshenger, com.afetiletisim.deprem field aliases)
            val senderId = when {
                json.has("senderId") -> json.getString("senderId")
                json.has("sender_id") -> json.getString("sender_id")
                json.has("userId") -> json.getString("userId")
                json.has("user_id") -> json.getString("user_id")
                json.has("author_id") -> json.getString("author_id")
                json.has("device_id") -> json.getString("device_id")
                json.has("node_id") -> json.getString("node_id")
                json.has("public_key") -> json.getString("public_key")
                json.has("mac") -> json.getString("mac")
                json.has("from") -> json.getString("from")
                else -> "EXT_NODE_" + UUID.randomUUID().toString().take(6)
            }

            // Extract Sender Name (Briar / Meshenger / Afet İletişim nick aliases)
            val senderName = when {
                json.has("senderName") -> json.getString("senderName")
                json.has("sender_name") -> json.getString("sender_name")
                json.has("userName") -> json.getString("userName")
                json.has("user_name") -> json.getString("user_name")
                json.has("name") -> json.getString("name")
                json.has("nick") -> json.getString("nick")
                json.has("nickname") -> json.getString("nickname")
                json.has("author") -> json.getString("author")
                json.has("alias") -> json.getString("alias")
                json.has("sender") -> json.getString("sender")
                json.has("user") -> json.getString("user")
                else -> "Off-Grid Afet Cihazı (${senderId.takeLast(4)})"
            }

            // Extract Recipient ID
            val recipientId = when {
                json.has("recipientId") -> json.getString("recipientId")
                json.has("recipient_id") -> json.getString("recipient_id")
                json.has("to") -> json.getString("to")
                json.has("destination") -> json.getString("destination")
                json.has("target") -> json.getString("target")
                else -> "*"
            }

            // Extract SOS Status
            val sosStatus = when {
                json.has("sosStatus") && !json.isNull("sosStatus") -> json.getString("sosStatus")
                json.has("sos_status") && !json.isNull("sos_status") -> json.getString("sos_status")
                json.has("status") && !json.isNull("status") -> json.getString("status")
                json.has("emergency_status") && !json.isNull("emergency_status") -> json.getString("emergency_status")
                json.has("emergency") && !json.isNull("emergency") -> json.getString("emergency")
                json.has("alarm") && !json.isNull("alarm") -> json.getString("alarm")
                else -> null
            }

            // Extract Packet Type
            val rawTypeStr = when {
                json.has("type") -> json.getString("type")
                json.has("packet_type") -> json.getString("packet_type")
                json.has("msg_type") -> json.getString("msg_type")
                json.has("kind") -> json.getString("kind")
                json.has("event") -> json.getString("event")
                else -> "CHAT_TEXT"
            }.uppercase()

            val packetType = when {
                sosStatus != null || rawTypeStr.contains("SOS") || rawTypeStr.contains("EMERGENCY") || rawTypeStr.contains("ALARM") -> PacketType.EMERGENCY_SOS
                rawTypeStr.contains("BEACON") || rawTypeStr.contains("DISCOVERY") || rawTypeStr.contains("PING") || rawTypeStr.contains("HEARTBEAT") -> PacketType.DISCOVERY_BEACON
                rawTypeStr.contains("VOICE") || rawTypeStr.contains("AUDIO") -> PacketType.VOICE_NOTE
                rawTypeStr.contains("VIDEO") || rawTypeStr.contains("CAM") -> PacketType.VIDEO_FRAME
                else -> PacketType.CHAT_TEXT
            }

            // Extract Payload / Message Content (Briar 'body', Meshenger 'message/text/body', com.afetiletisim 'message/text')
            val payload = when {
                json.has("payload") -> json.getString("payload")
                json.has("message") -> json.getString("message")
                json.has("body") -> json.getString("body")
                json.has("text") -> json.getString("text")
                json.has("msg") -> json.getString("msg")
                json.has("content") -> json.getString("content")
                json.has("data") -> json.getString("data")
                else -> ""
            }

            // Extract Latitude & Longitude
            val lat = when {
                json.has("latitude") -> json.optDouble("latitude")
                json.has("lat") -> json.optDouble("lat")
                json.has("location_lat") -> json.optDouble("location_lat")
                else -> Double.NaN
            }.takeIf { !it.isNaN() }

            val lon = when {
                json.has("longitude") -> json.optDouble("longitude")
                json.has("lon") -> json.optDouble("lon")
                json.has("lng") -> json.optDouble("lng")
                json.has("location_lng") -> json.optDouble("location_lng")
                else -> Double.NaN
            }.takeIf { !it.isNaN() }

            // Extract Battery
            val battery = when {
                json.has("senderBattery") -> json.optInt("senderBattery", -1)
                json.has("battery") -> json.optInt("battery", -1)
                json.has("bat") -> json.optInt("bat", -1)
                else -> -1
            }

            // Extract Packet ID
            val packetId = when {
                json.has("id") -> json.getString("id")
                json.has("packet_id") -> json.getString("packet_id")
                json.has("msg_id") -> json.getString("msg_id")
                else -> UUID.randomUUID().toString()
            }

            MeshPacket(
                id = packetId,
                senderId = senderId,
                senderName = senderName,
                recipientId = recipientId,
                type = packetType,
                payload = payload,
                senderBattery = battery,
                latitude = lat,
                longitude = lon,
                sosStatus = sosStatus
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse as external packet: ${e.message}")
            null
        }
    }

    /**
     * Formats an AfetMesh packet into an interoperable multi-field JSON string
     * compatible with third-party disaster apps (Briar, Meshenger, com.afetiletisim.deprem, etc.).
     */
    fun toInteropJson(packet: MeshPacket): String {
        val obj = JSONObject()
        // ID
        obj.put("id", packet.id)
        obj.put("packet_id", packet.id)
        obj.put("msg_id", packet.id)

        // Sender ID & Name aliases
        obj.put("senderId", packet.senderId)
        obj.put("sender_id", packet.senderId)
        obj.put("userId", packet.senderId)
        obj.put("author_id", packet.senderId)
        obj.put("senderName", packet.senderName)
        obj.put("sender_name", packet.senderName)
        obj.put("name", packet.senderName)
        obj.put("nick", packet.senderName)
        obj.put("sender", packet.senderName)
        obj.put("user", packet.senderName)

        // Recipient
        obj.put("recipientId", packet.recipientId)
        obj.put("to", packet.recipientId)

        // Type
        obj.put("type", packet.type.name)
        obj.put("packet_type", packet.type.name)
        obj.put("msg_type", packet.type.name)

        // Payload & Body Aliases (Briar, Meshenger & com.afetiletisim.deprem)
        obj.put("payload", packet.payload)
        obj.put("message", packet.payload)
        obj.put("msg", packet.payload)
        obj.put("text", packet.payload)
        obj.put("body", packet.payload)
        obj.put("content", packet.payload)

        // Timestamps & Hops
        obj.put("timestamp", packet.timestamp)
        obj.put("time", packet.timestamp)
        obj.put("ttl", packet.ttl)
        obj.put("hops", packet.hops)

        // Battery
        obj.put("senderBattery", packet.senderBattery)
        obj.put("battery", packet.senderBattery)

        // Location
        if (packet.latitude != null) {
            obj.put("latitude", packet.latitude)
            obj.put("lat", packet.latitude)
        }
        if (packet.longitude != null) {
            obj.put("longitude", packet.longitude)
            obj.put("lng", packet.longitude)
            obj.put("lon", packet.longitude)
        }

        // SOS Status
        if (packet.sosStatus != null) {
            obj.put("sosStatus", packet.sosStatus)
            obj.put("sos_status", packet.sosStatus)
            obj.put("status", packet.sosStatus)
            obj.put("emergency_status", packet.sosStatus)
        }
        return obj.toString()
    }
}
