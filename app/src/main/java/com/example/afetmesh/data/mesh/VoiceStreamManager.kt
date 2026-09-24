package com.example.afetmesh.data.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class VoiceStreamManager(private val context: Context) {

    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecordingPtt = false
    private var pttJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun initPlayer() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)
            audioTrack = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfigOut)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 4)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("VoiceStreamManager", "Player init error", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startPttStream(onAudioChunk: (String) -> Unit) {
        if (isRecordingPtt) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("VoiceStreamManager", "AudioRecord initialization failed (Permission missing?)")
                audioRecord?.release()
                audioRecord = null
                return
            }

            isRecordingPtt = true
            audioRecord?.startRecording()

            pttJob = scope.launch {
                val buffer = ByteArray(minBufferSize)
                while (isActive && isRecordingPtt) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        onAudioChunk(encoded)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceStreamManager", "Start PTT Stream error", e)
            isRecordingPtt = false
            stopPttStream()
        }
    }

    fun stopPttStream() {
        isRecordingPtt = false
        pttJob?.cancel()
        pttJob = null
        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playIncomingAudioChunk(base64Chunk: String) {
        try {
            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initPlayer()
            }
            val pcmBytes = Base64.decode(base64Chunk, Base64.NO_WRAP)
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Voice Note Recording (Records a short clip to send P2P)
    @SuppressLint("MissingPermission")
    fun recordVoiceNote(durationMs: Long = 5000, onComplete: (String) -> Unit) {
        scope.launch {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfigIn,
                    audioFormat,
                    minBufferSize * 4
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("VoiceStreamManager", "VoiceNote AudioRecord init failed")
                    recorder.release()
                    return@launch
                }

                val outputStream = ByteArrayOutputStream()
                recorder.startRecording()
                val buffer = ByteArray(minBufferSize)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < durationMs) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }

                recorder.stop()
                recorder.release()

                val fullPcm = outputStream.toByteArray()
                val encodedVoiceNote = Base64.encodeToString(fullPcm, Base64.NO_WRAP)
                withContext(Dispatchers.Main) {
                    onComplete(encodedVoiceNote)
                }
            } catch (e: Exception) {
                Log.e("VoiceStreamManager", "Record Voice Note error", e)
            }
        }
    }

    fun release() {
        stopPttStream()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scope.cancel()
    }
}
