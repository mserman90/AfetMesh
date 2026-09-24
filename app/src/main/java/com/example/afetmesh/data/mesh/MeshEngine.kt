package com.example.afetmesh.data.mesh

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.example.afetmesh.data.models.MeshPacket
import com.example.afetmesh.data.models.PacketType
import com.example.afetmesh.data.models.PeerNode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshEngine(private val context: Context) {

    val nodeId: String = "NODE_" + UUID.randomUUID().toString().take(6)
    var deviceName: String = Build.MODEL ?: "AfetCihaz"

    fun updateDeviceName(newName: String) {
        if (newName.isNotBlank()) {
            deviceName = newName.trim()
            bleMeshManager?.updateDeviceName(deviceName)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bleMeshManager: BleMeshManager? = null

    private val udpPort = 8888
    private val tcpPort = 8889

    private val _peers = MutableStateFlow<Map<String, PeerNode>>(emptyMap())
    val peers: StateFlow<Map<String, PeerNode>> = _peers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 100)
    val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    private val _activeSosAlerts = MutableStateFlow<List<MeshPacket>>(emptyList())
    val activeSosAlerts: StateFlow<List<MeshPacket>> = _activeSosAlerts.asStateFlow()

    private val _incomingVideoFrame = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 10) // (senderId, base64)
    val incomingVideoFrame: SharedFlow<Pair<String, String>> = _incomingVideoFrame.asSharedFlow()

    private val receivedPacketIds = ConcurrentHashMap.newKeySet<String>()

    var activeSosStatus: String? = null

    private var udpBeaconSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null

    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true

        if (bleMeshManager == null) {
            bleMeshManager = BleMeshManager(
                context = context,
                nodeId = nodeId,
                deviceName = deviceName,
                onPeerDiscovered = { updatePeer(it) },
                onPacketReceived = { processReceivedPacket(it, null) }
            )
        }
        bleMeshManager?.start()

        startUdpBeaconListener()
        startUdpBeaconBroadcaster()
        startTcpServer()
        startPeerCleanupLoop()
    }

    private fun startUdpBeaconListener() {
        scope.launch {
            try {
                udpBeaconSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(udpPort))
                    broadcast = true
                }
                val buffer = ByteArray(4096)
                while (isActive && isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpBeaconSocket?.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue

                    // Ignore own broadcast
                    if (isLocalAddress(packet.address)) continue

                    val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    try {
                        val meshPacket = json.decodeFromString<MeshPacket>(jsonStr)
                        if (meshPacket.type == PacketType.DISCOVERY_BEACON) {
                            val peer = PeerNode(
                                id = meshPacket.senderId,
                                name = meshPacket.senderName,
                                ipAddress = senderIp,
                                port = tcpPort,
                                lastSeen = System.currentTimeMillis(),
                                battery = meshPacket.senderBattery,
                                isDirect = true,
                                hops = 1,
                                sosStatus = meshPacket.sosStatus
                            )
                            updatePeer(peer)
                        }
                    } catch (e: Exception) {
                        Log.e("MeshEngine", "Failed to parse UDP beacon", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MeshEngine", "UDP Listener Error", e)
            }
        }
    }

    private fun startUdpBeaconBroadcaster() {
        scope.launch {
            while (isActive && isRunning) {
                try {
                    val beacon = MeshPacket(
                        id = UUID.randomUUID().toString(),
                        senderId = nodeId,
                        senderName = deviceName,
                        type = PacketType.DISCOVERY_BEACON,
                        senderBattery = getBatteryPercentage(),
                        sosStatus = activeSosStatus
                    )
                    val beaconJson = json.encodeToString(beacon).toByteArray(Charsets.UTF_8)
                    val broadcastAddress = getBroadcastAddress() ?: InetAddress.getByName("255.255.255.255")

                    val socket = DatagramSocket()
                    socket.broadcast = true
                    val packet = DatagramPacket(beaconJson, beaconJson.size, broadcastAddress, udpPort)
                    socket.send(packet)
                    socket.close()
                } catch (e: Exception) {
                    Log.e("MeshEngine", "UDP Beacon broadcast error", e)
                }
                delay(3000) // Broadcast presence every 3 seconds
            }
        }
    }

    private fun startTcpServer() {
        scope.launch {
            try {
                tcpServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(tcpPort))
                }
                while (isActive && isRunning) {
                    val clientSocket = tcpServerSocket?.accept() ?: break
                    scope.launch { handleTcpClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e("MeshEngine", "TCP Server Error", e)
            }
        }
    }

    private fun handleTcpClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val line = reader.readLine() ?: return
            val meshPacket = json.decodeFromString<MeshPacket>(line)

            processReceivedPacket(meshPacket, socket.inetAddress.hostAddress)
            socket.close()
        } catch (e: Exception) {
            Log.e("MeshEngine", "TCP Client handle error", e)
        }
    }

    fun sendPacket(packet: MeshPacket) {
        scope.launch {
            receivedPacketIds.add(packet.id) // Mark own packet as seen
            val packetWithBattery = packet.copy(senderBattery = getBatteryPercentage())
            val serialized = json.encodeToString(packetWithBattery)

            if (packet.recipientId != "*") {
                // Direct unicast
                val peer = _peers.value[packet.recipientId]
                if (peer != null) {
                    sendTcpRaw(peer.ipAddress, peer.port, serialized)
                }
            } else {
                // Broadcast to all known direct peers
                _peers.value.values.forEach { peer ->
                    if (peer.isDirect) {
                        sendTcpRaw(peer.ipAddress, peer.port, serialized)
                    }
                }
            }
        }
    }

    private fun sendTcpRaw(ip: String, port: Int, rawJson: String) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 3000)
            val os = socket.getOutputStream()
            val bytes = (rawJson + "\n").toByteArray(Charsets.UTF_8)
            os.write(bytes)
            os.flush()
            socket.close()
        } catch (e: Exception) {
            Log.e("MeshEngine", "Failed to send TCP to $ip:$port", e)
        }
    }

    private fun processReceivedPacket(packet: MeshPacket, senderIp: String?) {
        if (!receivedPacketIds.add(packet.id)) return // Already processed (Deduplicated)

        // Store peer if sender is new
        if (senderIp != null && packet.senderId != nodeId) {
            val peer = PeerNode(
                id = packet.senderId,
                name = packet.senderName,
                ipAddress = senderIp,
                port = tcpPort,
                lastSeen = System.currentTimeMillis(),
                battery = packet.senderBattery,
                sosStatus = packet.sosStatus
            )
            updatePeer(peer)
        }

        // Separate handling for video stream frames for high performance
        if (packet.type == PacketType.VIDEO_FRAME) {
            _incomingVideoFrame.tryEmit(Pair(packet.senderId, packet.payload))
            return
        }

        // Emergency SOS handling
        if (packet.type == PacketType.EMERGENCY_SOS) {
            val currentList = _activeSosAlerts.value.toMutableList()
            currentList.removeAll { it.senderId == packet.senderId }
            currentList.add(0, packet)
            _activeSosAlerts.value = currentList
        }

        _incomingPackets.tryEmit(packet)

        // BRIAR MESH STORE & FORWARD RELAY
        if (packet.ttl > 1) {
            val relayedPacket = packet.copy(ttl = packet.ttl - 1, hops = packet.hops + 1)
            scope.launch {
                val serialized = json.encodeToString(relayedPacket)
                _peers.value.values.forEach { peer ->
                    // Relay to all peers except original sender
                    if (peer.ipAddress != senderIp) {
                        sendTcpRaw(peer.ipAddress, peer.port, serialized)
                    }
                }
            }
        }
    }

    private fun updatePeer(peer: PeerNode) {
        val current = _peers.value.toMutableMap()
        current[peer.id] = peer
        _peers.value = current
    }

    private fun startPeerCleanupLoop() {
        scope.launch {
            while (isActive && isRunning) {
                delay(10000)
                val now = System.currentTimeMillis()
                val current = _peers.value.toMutableMap()
                // Mark peers offline if not seen for 25 seconds
                val offlineKeys = current.filter { now - it.value.lastSeen > 25000 }.keys
                if (offlineKeys.isNotEmpty()) {
                    offlineKeys.forEach { current.remove(it) }
                    _peers.value = current
                }
            }
        }
    }

    private fun isLocalAddress(addr: InetAddress): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    if (addresses.nextElement() == addr) return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun getBroadcastAddress(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) return broadcast
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
    }

    fun stop() {
        isRunning = false
        try {
            bleMeshManager?.stop()
            udpBeaconSocket?.close()
            tcpServerSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scope.cancel()
    }
}
