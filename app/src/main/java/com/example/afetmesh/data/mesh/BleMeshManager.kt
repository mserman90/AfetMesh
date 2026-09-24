package com.example.afetmesh.data.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.afetmesh.data.models.MeshPacket
import com.example.afetmesh.data.models.PacketType
import com.example.afetmesh.data.models.PeerNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class BleMeshManager(
    private val context: Context,
    private val nodeId: String,
    private var deviceName: String,
    private val onPeerDiscovered: (PeerNode) -> Unit,
    private val onPacketReceived: (MeshPacket) -> Unit
) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter
    }

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var isRunning = false
        private set

    companion object {
        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000AFE1-0000-1000-8000-00805F9B34FB")
        private const val TAG = "BleMeshManager"
    }

    fun updateDeviceName(newName: String) {
        this.deviceName = newName
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled or not supported on this device.")
            return
        }

        isRunning = true
        startAdvertising()
        startScanning()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        try {
            bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            if (bleAdvertiser == null) return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "BLE Advertising started successfully.")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "BLE Advertising failed with code: $errorCode")
                }
            }

            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "BLE Advertising exception", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        try {
            bleScanner = bluetoothAdapter?.bluetoothLeScanner
            if (bleScanner == null) return

            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { handleScanResult(it) }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { handleScanResult(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "BLE Scan failed with code: $errorCode")
                }
            }

            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "BLE Scan exception", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val rssi = result.rssi
        val name = device.name ?: "BLE Cihazı (${device.address.takeLast(5)})"

        val peer = PeerNode(
            id = "BLE_" + device.address.replace(":", "").takeLast(6),
            name = name,
            ipAddress = "BLE (${device.address})",
            port = 0,
            lastSeen = System.currentTimeMillis(),
            battery = -1,
            isDirect = true,
            hops = 1
        )

        onPeerDiscovered(peer)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        isRunning = false
        try {
            if (advertiseCallback != null) {
                bleAdvertiser?.stopAdvertising(advertiseCallback)
            }
            if (scanCallback != null) {
                bleScanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "BLE Stop exception", e)
        }
    }
}
