package com.example.afetmesh.ui.main

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.afetmesh.data.models.MeshPacket
import com.example.afetmesh.data.models.PacketType
import com.example.afetmesh.data.models.PeerNode
import com.example.afetmesh.data.models.SosStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainScreenViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val activeSosAlerts by viewModel.activeSosAlerts.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val activeSosStatus by viewModel.activeSosStatus.collectAsState()
    val incomingVideoBitmap by viewModel.incomingVideoBitmap.collectAsState()
    val userName by viewModel.userName.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showNameDialog by remember { mutableStateOf(false) }
    var tempNameInput by remember(userName) { mutableStateOf(userName) }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Kullanıcı Profili / İsim Girişi", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Afet mesh ağında diğer kişilerin sizi tanıması için adınızı/takma adınızı yazın:", fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempNameInput,
                        onValueChange = { tempNameInput = it },
                        label = { Text("Adınız & Soyadınız / Çağrı Adınız") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempNameInput.isNotBlank()) {
                            viewModel.setUserName(tempNameInput)
                        }
                        showNameDialog = false
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "AfetMesh",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF4CAF50),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "OFF-GRID",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Kullanıcı: $userName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { showNameDialog = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "İsim Düzenle",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    currentLocation?.let { loc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "GPS",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                String.format(Locale.US, "%.3f,%.3f", loc.latitude, loc.longitude),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeSosAlerts.isNotEmpty()) {
                                    Badge { Text("${activeSosAlerts.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "ACİL SOS", tint = Color.Red)
                        }
                    },
                    label = { Text("SOS", color = if (selectedTab == 0) Color.Red else Color.Unspecified) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Yazılı") },
                    label = { Text("Yazılı") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Sesli") },
                    label = { Text("Sesli (PTT)") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = "Görüntülü") },
                    label = { Text("Görüntülü") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Harita") },
                    label = { Text("Harita") }
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (peers.isNotEmpty()) {
                                    Badge { Text("${peers.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = "Radar")
                        }
                    },
                    label = { Text("Radar") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> SosTabContent(
                    activeSosStatus = activeSosStatus,
                    activeSosAlerts = activeSosAlerts,
                    onSendSos = { status -> viewModel.sendSos(status) },
                    onCancelSos = { viewModel.cancelSos() },
                    isSirenActive = viewModel.isSirenActive,
                    onToggleSiren = { viewModel.toggleSiren() },
                    isStrobeActive = viewModel.isStrobeActive,
                    onToggleStrobe = { viewModel.toggleStrobe() }
                )
                1 -> TextChatTabContent(
                    messages = messages,
                    onSendMessage = { text -> viewModel.sendTextMessage(text) },
                    onSendVoiceNote = { viewModel.sendVoiceNote() }
                )
                2 -> VoicePttTabContent(
                    peersCount = peers.size,
                    onStartPtt = { viewModel.startPtt() },
                    onStopPtt = { viewModel.stopPtt() }
                )
                3 -> VideoCallTabContent(
                    incomingVideoBitmap = incomingVideoBitmap,
                    isVideoStreaming = viewModel.isVideoStreamActive,
                    onStartStream = { lifecycleOwner -> viewModel.startVideoStream(lifecycleOwner) },
                    onStopStream = { viewModel.stopVideoStream() }
                )
                4 -> com.example.afetmesh.ui.map.OfflineMapScreen(
                    currentLocation = currentLocation,
                    sosAlerts = activeSosAlerts,
                    peers = peers.values.toList(),
                    onAddCustomPoint = { pt ->
                        viewModel.sendTextMessage("📌 YENİ AFET NOKTASI: ${pt.name} (${pt.description})")
                    }
                )
                5 -> RadarTabContent(
                    peers = peers.values.toList(),
                    currentNodeId = viewModel.repository.meshEngine.nodeId
                )
            }
        }
    }
}

// ------------------- 1. ACİL SOS TAB -------------------
@Composable
fun SosTabContent(
    activeSosStatus: String?,
    activeSosAlerts: List<MeshPacket>,
    onSendSos: (String) -> Unit,
    onCancelSos: () -> Unit,
    isSirenActive: Boolean,
    onToggleSiren: () -> Unit,
    isStrobeActive: Boolean,
    onToggleStrobe: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSosStatus != null) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🚨 ACİL AFET BEACON",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Red
                    )
                    Spacer(Modifier.height(8.dp))

                    if (activeSosStatus != null) {
                        Text(
                            "AKTİF SOS BİLDİRİMİNİZ:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            activeSosStatus,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Red
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onCancelSos,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("SOS BİLDİRİMİNİ İPTAL ET")
                        }
                    } else {
                        Text(
                            "Durumunuzu seçerek tüm yakındaki telefonlara anında ACİL SOS sinyali yayınlayın:",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            Text(
                "SOS Durumu Seçin ve Yayınlayın:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SosStatus.ALL.chunked(2).forEach { rowStatuses ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowStatuses.forEach { status ->
                            Button(
                                onClick = { onSendSos(status) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (status) {
                                        SosStatus.TRAPPED, SosStatus.INJURED -> Color(0xFFD32F2F)
                                        SosStatus.SAFE -> Color(0xFF388E3C)
                                        else -> Color(0xFFE65100)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    status,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Arama-Kurtarma Yardım Araçları:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleSiren,
                            modifier = Modifier.weight(1f),
                            colors = if (isSirenActive) ButtonDefaults.outlinedButtonColors(containerColor = Color.Red, contentColor = Color.White) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isSirenActive) "Siren Kapat" else "Yüksek Siren Ses")
                        }

                        OutlinedButton(
                            onClick = onToggleStrobe,
                            modifier = Modifier.weight(1f),
                            colors = if (isStrobeActive) ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFF9800), contentColor = Color.White) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isStrobeActive) "Flaş Kapat" else "SOS Flaşör")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Ağdaki Aktif Acil SOS Çağrıları (${activeSosAlerts.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Red
            )
        }

        if (activeSosAlerts.isEmpty()) {
            item {
                Text(
                    "Şu an yakındaki mesh ağında aktif SOS çağrısı yok.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(activeSosAlerts) { sos ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    border = CardDefaults.outlinedCardBorder(enabled = true),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(sos.senderName, fontWeight = FontWeight.Bold, color = Color.Red)
                            Text("Pil: %${sos.senderBattery}", fontSize = 11.sp, color = Color.DarkGray)
                        }
                        Text(sos.sosStatus ?: sos.payload, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFB71C1C))
                        if (sos.latitude != null && sos.longitude != null) {
                            Text(
                                "Konum: ${sos.latitude}, ${sos.longitude}",
                                fontSize = 12.sp,
                                color = Color(0xFF0D47A1)
                            )
                        }
                        Text("Zaman: ${formatTime(sos.timestamp)} | Sıçrama (Hop): ${sos.hops}", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ------------------- 2. YAZILI HABERLEŞME TAB -------------------
@Composable
fun TextChatTabContent(
    messages: List<MeshPacket>,
    onSendMessage: (String) -> Unit,
    onSendVoiceNote: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // Quick Emergency Presets
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "Güvendeyim 👍",
                "Enkaz altındayım 🚨",
                "Su / Yiyecek lazım 💧",
                "Tıbbi yardım lazım 🚑",
                "Burada 3 kişiyiz 👥"
            )
            items(presets) { preset ->
                SuggestionChip(
                    onClick = { onSendMessage(preset) },
                    label = { Text(preset, fontSize = 11.sp) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                val isSelf = msg.senderId.startsWith("NODE_") // Simplification for UI display
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                msg.senderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            if (msg.type == PacketType.VOICE_NOTE) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFE65100))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ses Kaydı (Off-Grid Voice)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Text(msg.payload, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(formatTime(msg.timestamp), fontSize = 9.sp, color = Color.Gray)
                                if (msg.senderBattery > 0) {
                                    Text("Pil: %${msg.senderBattery}", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSendVoiceNote) {
                Icon(Icons.Default.Mic, contentDescription = "Ses Kaydet", tint = Color(0xFFE65100))
            }
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Afet mesajı yazın...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Gönder", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ------------------- 3. SESLİ (PTT) TAB -------------------
@SuppressLint("ClickableViewAccessibility")
@Composable
fun VoicePttTabContent(
    peersCount: Int,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit
) {
    var isPressingPtt by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "PUSH-TO-TALK (BAS-KONUŞ)",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Telsiz Modu: İnternetsiz Doğrudan Ses Yayını",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(32.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(if (isPressingPtt) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressingPtt = true
                            onStartPtt()
                            tryAwaitRelease()
                            isPressingPtt = false
                            onStopPtt()
                        }
                    )
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "PTT",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isPressingPtt) "KONUŞUN (CANLI)" else "BASILI TUTUN",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dinleyen Cihaz Sayısı:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("$peersCount Cihaz", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
            }
        }
    }
}

// ------------------- 4. GÖRÜNTÜLÜ (VIDEO CALL) TAB -------------------
@Composable
fun VideoCallTabContent(
    incomingVideoBitmap: Bitmap?,
    isVideoStreaming: Boolean,
    onStartStream: (LifecycleOwner) -> Unit,
    onStopStream: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OFF-GRID GÖRÜNTÜLÜ HABERLEŞME", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("P2P İnternetsiz Canlı Video Akışı", fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        // Video Display Screen
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(2.dp, Color.Gray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (incomingVideoBitmap != null) {
                Image(
                    bitmap = incomingVideoBitmap.asImageBitmap(),
                    contentDescription = "Gelen Görüntü",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Gelen Canlı Görüntü Yok", color = Color.Gray, fontSize = 13.sp)
                }
            }

            if (isVideoStreaming) {
                Surface(
                    color = Color.Red,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text("YAYINDASINIZ (LIVE)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (isVideoStreaming) {
                        onStopStream()
                    } else {
                        onStartStream(lifecycleOwner)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVideoStreaming) Color.Red else Color(0xFF2196F3)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(if (isVideoStreaming) Icons.Default.CallEnd else Icons.Default.Videocam, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isVideoStreaming) "YAYINI BİTİR" else "CANLI VİDEO YAYINI BAŞLAT")
            }
        }
    }
}

// ------------------- 5. MESH RADAR TAB -------------------
@Composable
fun RadarTabContent(
    peers: List<PeerNode>,
    currentNodeId: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mesh Ağ Radarı", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Aktif Düğüm: ${peers.size}", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C), fontSize = 13.sp)
        }
        Text("Çevredeki kapsama alanındaki internetsiz cihazlar:", fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        if (peers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Yakında bağlı AfetMesh cihazı aranıyor...", color = Color.Gray, fontSize = 13.sp)
                    Text("(Yakındaki telefonların aynı Wi-Fi/Hotspot ağında olduğundan emin olun)", fontSize = 10.sp, color = Color.LightGray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers) { peer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (peer.sosStatus != null) Color.Red else Color(0xFF4CAF50))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(peer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("ID: ${peer.id} | IP: ${peer.ipAddress}", fontSize = 11.sp, color = Color.Gray)
                                if (peer.sosStatus != null) {
                                    Text("🚨 SOS: ${peer.sosStatus}", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 12.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (peer.battery > 0) {
                                    Text("Pil: %${peer.battery}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                Text("Hop: ${peer.hops}", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
