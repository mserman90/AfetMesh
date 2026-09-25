package com.example.afetmesh.data.mesh

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*

class DisasterBeaconManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sirenJob: Job? = null
    private var strobeJob: Job? = null

    private var toneGenerator: ToneGenerator? = null
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var whistleTrack: android.media.AudioTrack? = null
    private var whistleJob: Job? = null

    var isSirenActive = false
        private set

    var isStrobeActive = false
        private set

    var isWhistleActive = false
        private set

    fun startDigitalWhistle() {
        if (isWhistleActive) return
        isWhistleActive = true

        whistleJob = scope.launch {
            try {
                val sampleRate = 44100
                val freq = 3200.0 // 3.2 kHz High-pitched rescue whistle frequency
                val buffSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                val buffer = ShortArray(buffSize)

                @Suppress("DEPRECATION")
                whistleTrack = android.media.AudioTrack(
                    AudioManager.STREAM_ALARM,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    buffSize,
                    android.media.AudioTrack.MODE_STREAM
                )

                whistleTrack?.play()

                var angle = 0.0
                while (isActive && isWhistleActive) {
                    for (i in buffer.indices) {
                        buffer[i] = (Math.sin(angle) * 32767).toInt().toShort()
                        angle += 2.0 * Math.PI * freq / sampleRate
                        if (angle > 2.0 * Math.PI) angle -= 2.0 * Math.PI
                    }
                    whistleTrack?.write(buffer, 0, buffer.size)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopDigitalWhistle() {
        isWhistleActive = false
        whistleJob?.cancel()
        whistleJob = null
        try {
            whistleTrack?.stop()
            whistleTrack?.release()
            whistleTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startSirenAlert() {
        if (isSirenActive) return
        isSirenActive = true

        sirenJob = scope.launch {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                while (isActive && isSirenActive) {
                    // High-pitched disaster alert siren tone
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
                    
                    // Strong emergency vibration pattern
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0, 400, 200, 400),
                                -1
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 400, 200, 400), -1)
                    }
                    
                    delay(1000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopSirenAlert() {
        isSirenActive = false
        sirenJob?.cancel()
        sirenJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
            vibrator.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startFlashlightSosStrobe() {
        if (isStrobeActive) return
        isStrobeActive = true

        strobeJob = scope.launch {
            val cameraId = try {
                cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (e: Exception) {
                null
            }

            if (cameraId == null) {
                isStrobeActive = false
                return@launch
            }

            // Morse Code for S.O.S: ... --- ...
            // Dot: 150ms, Dash: 450ms, Element space: 150ms, Letter space: 450ms, Word space: 1000ms
            while (isActive && isStrobeActive) {
                try {
                    // S: ...
                    repeat(3) {
                        setFlashlight(cameraId, true)
                        delay(150)
                        setFlashlight(cameraId, false)
                        delay(150)
                    }
                    delay(300) // Space between S and O

                    // O: ---
                    repeat(3) {
                        setFlashlight(cameraId, true)
                        delay(450)
                        setFlashlight(cameraId, false)
                        delay(150)
                    }
                    delay(300) // Space between O and S

                    // S: ...
                    repeat(3) {
                        setFlashlight(cameraId, true)
                        delay(150)
                        setFlashlight(cameraId, false)
                        delay(150)
                    }

                    delay(1200) // Pause before repeating SOS loop
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
            setFlashlight(cameraId, false)
        }
    }

    fun stopFlashlightSosStrobe() {
        isStrobeActive = false
        strobeJob?.cancel()
        strobeJob = null
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                setFlashlight(cameraId, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setFlashlight(cameraId: String, state: Boolean) {
        try {
            cameraManager.setTorchMode(cameraId, state)
        } catch (e: Exception) {
            // Ignore if camera is in use or flash unavailable
        }
    }

    fun release() {
        stopDigitalWhistle()
        stopSirenAlert()
        stopFlashlightSosStrobe()
        scope.cancel()
    }
}
