package com.example.afetmesh.data

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import androidx.lifecycle.LifecycleOwner
import com.example.afetmesh.data.mesh.*
import com.example.afetmesh.data.models.MeshPacket
import com.example.afetmesh.data.models.PacketType
import com.example.afetmesh.data.models.PeerNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class AfetMeshRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("afetmesh_prefs", Context.MODE_PRIVATE)
    private val _userName = MutableStateFlow(
        prefs.getString("user_name", null) ?: (android.os.Build.MODEL ?: "Afet Kullanıcısı")
    )
    val userName: StateFlow<String> = _userName.asStateFlow()

    val meshEngine = MeshEngine(context)
    val beaconManager = DisasterBeaconManager(context)
    val voiceStreamManager = VoiceStreamManager(context)
    val videoStreamManager = VideoStreamManager(context)
    val locationHelper = LocationHelper(context)
    val eDevletAfadManager = EDevletAfadManager(context)

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _messages = MutableStateFlow<List<MeshPacket>>(emptyList())
    val messages: StateFlow<List<MeshPacket>> = _messages.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _activeSosStatus = MutableStateFlow<String?>(null)
    val activeSosStatus: StateFlow<String?> = _activeSosStatus.asStateFlow()

    private val _incomingVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val incomingVideoBitmap: StateFlow<Bitmap?> = _incomingVideoBitmap.asStateFlow()

    val peers: StateFlow<Map<String, PeerNode>> = meshEngine.peers
    val activeSosAlerts: StateFlow<List<MeshPacket>> = meshEngine.activeSosAlerts

    val uniqueDigitalId: String get() = "AFET-UUID-" + meshEngine.nodeId.replace("NODE_", "").uppercase()

    init {
        meshEngine.deviceName = _userName.value
        meshEngine.start()
        AfetMeshBackgroundService.startService(context)
        locationHelper.startLocationUpdates { loc ->
            _currentLocation.value = loc
        }

        // Collect incoming packets
        scope.launch {
            meshEngine.incomingPackets.collect { packet ->
                when (packet.type) {
                    PacketType.CHAT_TEXT, PacketType.VOICE_NOTE, PacketType.PHOTO_TRANSFER -> {
                        val list = _messages.value.toMutableList()
                        list.add(packet)
                        _messages.value = list
                    }
                    PacketType.AUDIO_STREAM -> {
                        voiceStreamManager.playIncomingAudioChunk(packet.payload)
                    }
                    else -> {}
                }
            }
        }

        // Collect incoming video frames
        scope.launch {
            meshEngine.incomingVideoFrame.collect { (_, base64Frame) ->
                val bmp = VideoStreamManager.decodeBase64ToBitmap(base64Frame)
                _incomingVideoBitmap.value = bmp
            }
        }
    }

    fun sendTextMessage(text: String, recipientId: String = "*") {
        val loc = _currentLocation.value
        val packet = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = meshEngine.nodeId,
            senderName = meshEngine.deviceName,
            recipientId = recipientId,
            type = PacketType.CHAT_TEXT,
            payload = text,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            sosStatus = _activeSosStatus.value
        )
        val list = _messages.value.toMutableList()
        list.add(packet)
        _messages.value = list

        meshEngine.sendPacket(packet)
    }

    fun sendSosBroadcast(status: String) {
        val isSafeStatus = status.contains("GÜVENDE", ignoreCase = true)

        if (isSafeStatus) {
            // Cancel active siren and flashlight strobe when user reports SAFE
            beaconManager.stopSirenAlert()
            beaconManager.stopFlashlightSosStrobe()
            _activeSosStatus.value = status
            meshEngine.activeSosStatus = status
            val loc = _currentLocation.value

            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = meshEngine.nodeId,
                senderName = meshEngine.deviceName,
                recipientId = "*", // Broadcast
                type = PacketType.EMERGENCY_SOS,
                payload = "DURUM BİLDİRİMİ: $status",
                latitude = loc?.latitude,
                longitude = loc?.longitude,
                sosStatus = status
            )

            meshEngine.sendPacket(packet)
        } else {
            _activeSosStatus.value = status
            meshEngine.activeSosStatus = status
            val loc = _currentLocation.value

            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = meshEngine.nodeId,
                senderName = meshEngine.deviceName,
                recipientId = "*", // Broadcast
                type = PacketType.EMERGENCY_SOS,
                payload = "ACİL SOS BİLDİRİMİ: $status",
                latitude = loc?.latitude,
                longitude = loc?.longitude,
                sosStatus = status
            )

            meshEngine.sendPacket(packet)

            // Trigger local beacon alerts for actual emergency SOS statuses
            beaconManager.startSirenAlert()
            beaconManager.startFlashlightSosStrobe()
        }
    }

    fun cancelSos() {
        _activeSosStatus.value = null
        meshEngine.activeSosStatus = null
        beaconManager.stopSirenAlert()
        beaconManager.stopFlashlightSosStrobe()
    }

    fun toggleWhistle() {
        if (beaconManager.isWhistleActive) {
            beaconManager.stopDigitalWhistle()
        } else {
            beaconManager.startDigitalWhistle()
        }
    }

    fun toggleSiren() {
        if (beaconManager.isSirenActive) {
            beaconManager.stopSirenAlert()
        } else {
            beaconManager.startSirenAlert()
        }
    }

    fun toggleStrobe() {
        if (beaconManager.isStrobeActive) {
            beaconManager.stopFlashlightSosStrobe()
        } else {
            beaconManager.startFlashlightSosStrobe()
        }
    }

    fun playVoiceNote(base64Payload: String) {
        voiceStreamManager.playVoiceNote(base64Payload)
    }

    fun deleteMessage(messageId: String) {
        val list = _messages.value.toMutableList()
        list.removeAll { it.id == messageId }
        _messages.value = list
    }

    fun startPtt(recipientId: String = "*") {
        voiceStreamManager.startPttStream { base64Chunk ->
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = meshEngine.nodeId,
                senderName = meshEngine.deviceName,
                recipientId = recipientId,
                type = PacketType.AUDIO_STREAM,
                payload = base64Chunk
            )
            meshEngine.sendPacket(packet)
        }
    }

    fun stopPtt() {
        voiceStreamManager.stopPttStream()
    }

    fun recordAndSendVoiceNote(durationMs: Long = 5000, recipientId: String = "*") {
        voiceStreamManager.recordVoiceNote(durationMs) { base64VoiceNote ->
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = meshEngine.nodeId,
                senderName = meshEngine.deviceName,
                recipientId = recipientId,
                type = PacketType.VOICE_NOTE,
                payload = base64VoiceNote
            )
            val list = _messages.value.toMutableList()
            list.add(packet)
            _messages.value = list

            meshEngine.sendPacket(packet)
        }
    }

    fun startVideoStream(lifecycleOwner: LifecycleOwner, recipientId: String = "*") {
        videoStreamManager.startVideoStream(lifecycleOwner) { base64Frame ->
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = meshEngine.nodeId,
                senderName = meshEngine.deviceName,
                recipientId = recipientId,
                type = PacketType.VIDEO_FRAME,
                payload = base64Frame
            )
            meshEngine.sendPacket(packet)
        }
    }

    fun stopVideoStream() {
        videoStreamManager.stopVideoStream()
    }

    fun setUserName(name: String) {
        if (name.isNotBlank()) {
            val cleanName = name.trim()
            _userName.value = cleanName
            prefs.edit().putString("user_name", cleanName).apply()
            meshEngine.updateDeviceName(cleanName)
        }
    }

    fun cleanup() {
        meshEngine.stop()
        beaconManager.release()
        voiceStreamManager.release()
    }
}
