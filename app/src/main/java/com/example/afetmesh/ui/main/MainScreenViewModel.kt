package com.example.afetmesh.ui.main

import android.app.Application
import android.graphics.Bitmap
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import com.example.afetmesh.data.AfetMeshRepository
import com.example.afetmesh.data.models.MeshPacket
import com.example.afetmesh.data.models.PeerNode
import kotlinx.coroutines.flow.StateFlow

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AfetMeshRepository(application.applicationContext)

    val messages: StateFlow<List<MeshPacket>> = repository.messages
    val peers: StateFlow<Map<String, PeerNode>> = repository.peers
    val activeSosAlerts: StateFlow<List<MeshPacket>> = repository.activeSosAlerts
    val currentLocation: StateFlow<Location?> = repository.currentLocation
    val activeSosStatus: StateFlow<String?> = repository.activeSosStatus
    val incomingVideoBitmap: StateFlow<Bitmap?> = repository.incomingVideoBitmap
    val userName: StateFlow<String> = repository.userName

    fun setUserName(name: String) {
        repository.setUserName(name)
    }

    val isSirenActive: Boolean
        get() = repository.beaconManager.isSirenActive

    val isStrobeActive: Boolean
        get() = repository.beaconManager.isStrobeActive

    val isVideoStreamActive: Boolean
        get() = repository.videoStreamManager.isStreamingVideo

    fun sendTextMessage(text: String, recipientId: String = "*") {
        if (text.isNotBlank()) {
            repository.sendTextMessage(text, recipientId)
        }
    }

    fun sendSos(status: String) {
        repository.sendSosBroadcast(status)
    }

    fun cancelSos() {
        repository.cancelSos()
    }

    fun toggleSiren() {
        repository.toggleSiren()
    }

    fun toggleStrobe() {
        repository.toggleStrobe()
    }

    fun startPtt(recipientId: String = "*") {
        repository.startPtt(recipientId)
    }

    fun stopPtt() {
        repository.stopPtt()
    }

    fun sendVoiceNote(recipientId: String = "*") {
        repository.recordAndSendVoiceNote(durationMs = 4000, recipientId = recipientId)
    }

    fun startVideoStream(lifecycleOwner: LifecycleOwner, recipientId: String = "*") {
        repository.startVideoStream(lifecycleOwner, recipientId)
    }

    fun stopVideoStream() {
        repository.stopVideoStream()
    }

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
    }
}
