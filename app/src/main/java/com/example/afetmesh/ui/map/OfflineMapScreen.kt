package com.example.afetmesh.ui.map

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.afetmesh.data.models.DefaultDisasterPoints
import com.example.afetmesh.data.models.DisasterPoint
import com.example.afetmesh.data.models.MeshPacket
import com.example.afetmesh.data.models.PeerNode
import com.example.afetmesh.data.models.PointType
import kotlin.math.*

import com.example.afetmesh.data.mesh.EDevletAfadManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    currentLocation: Location?,
    sosAlerts: List<MeshPacket>,
    peers: List<PeerNode>,
    onAddCustomPoint: (DisasterPoint) -> Unit,
    eDevletAfadManager: EDevletAfadManager? = null
) {
    var centerLat by remember { mutableDoubleStateOf(currentLocation?.latitude ?: 41.0082) }
    var centerLon by remember { mutableDoubleStateOf(currentLocation?.longitude ?: 28.9784) }
    var zoomLevel by remember { mutableFloatStateOf(1000f) } // Pixels per degree

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var selectedPoint by remember { mutableStateOf<DisasterPoint?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Keep map centered on user GPS when available
    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            centerLat = currentLocation.latitude
            centerLon = currentLocation.longitude
        }
    }

    val assemblyPoints = remember(currentLocation) {
        eDevletAfadManager?.getAssemblyPointsForLocation(currentLocation?.latitude, currentLocation?.longitude)
            ?: DefaultDisasterPoints.PRELOADED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Map Header & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ÇEVRİMDİŞİ AFET HARİTASI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("e-Devlet AFAD ve Mesh Toplanma Noktaları", fontSize = 11.sp, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        currentLocation?.let {
                            centerLat = it.latitude
                            centerLon = it.longitude
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Konumuma Git", tint = Color(0xFF2196F3))
                }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.AddLocation, contentDescription = "Nokta Ekle", tint = Color(0xFF4CAF50))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // e-Devlet AFAD Acil Toplanma Alanı Sorgulama Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "e-Devlet Onaylı",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "e-Devlet AFAD Toplanma Alanı",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (currentLocation != null) "GPS Konumuna Göre Çevrimdışı Listelendi" else "Varsayılan İl/İlçe Verisi Gösteriliyor",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }
                Button(
                    onClick = { eDevletAfadManager?.openEDevletQueryInBrowser() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("e-Devlet'te Aç", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Interactive Map Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomLevel = (zoomLevel * zoom).coerceIn(400f, 5000f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerPxX = canvasWidth / 2f + offsetX
                val centerPxY = canvasHeight / 2f + offsetY

                fun latLonToPx(lat: Double, lon: Double): Offset {
                    val x = centerPxX + (lon - centerLon).toFloat() * zoomLevel
                    val y = centerPxY - (lat - centerLat).toFloat() * zoomLevel
                    return Offset(x, y)
                }

                // Grid lines (Distance scale reference)
                val gridSpacing = 60.0.dp.toPx()
                var gridX = 0f
                while (gridX < canvasWidth) {
                    drawLine(
                        color = Color(0xFF334155),
                        start = Offset(gridX, 0f),
                        end = Offset(gridX, canvasHeight),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    gridX += gridSpacing
                }

                var gridY = 0f
                while (gridY < canvasHeight) {
                    drawLine(
                        color = Color(0xFF334155),
                        start = Offset(0f, gridY),
                        end = Offset(canvasWidth, gridY),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    gridY += gridSpacing
                }

                // 1. Draw Preloaded Emergency Assembly Points
                assemblyPoints.forEach { pt ->
                    val pos = latLonToPx(pt.latitude, pt.longitude)
                    val color = when (pt.type) {
                        PointType.ASSEMBLY_AREA -> Color(0xFF4CAF50)
                        PointType.MEDICAL_TENT -> Color(0xFFE91E63)
                        PointType.WATER_SUPPLY -> Color(0xFF00BCD4)
                        else -> Color(0xFFFF9800)
                    }

                    drawCircle(color = color.copy(alpha = 0.3f), radius = 18f, center = pos)
                    drawCircle(color = color, radius = 10f, center = pos)
                    drawCircle(color = Color.White, radius = 4f, center = pos)
                }

                // 2. Draw Active SOS Emergency Locations (Red Pulsing Rings)
                sosAlerts.forEach { sos ->
                    if (sos.latitude != null && sos.longitude != null) {
                        val pos = latLonToPx(sos.latitude, sos.longitude)
                        drawCircle(color = Color.Red.copy(alpha = 0.4f), radius = 28f, center = pos)
                        drawCircle(color = Color.Red, radius = 12f, center = pos)
                        drawCircle(color = Color.White, radius = 5f, center = pos)
                    }
                }

                // 3. Draw User's Current GPS Location (Blue Pin)
                currentLocation?.let { loc ->
                    val userPos = latLonToPx(loc.latitude, loc.longitude)
                    drawCircle(color = Color(0xFF2196F3).copy(alpha = 0.3f), radius = 32f, center = userPos)
                    drawCircle(color = Color(0xFF2196F3), radius = 12f, center = userPos)
                    drawCircle(color = Color.White, radius = 5f, center = userPos)
                }
            }

            // Legend Overlay
            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    LegendItem(Color(0xFF2196F3), "SİZ (GPS)")
                    LegendItem(Color.Red, "ACİL SOS ÇAĞRISI")
                    LegendItem(Color(0xFF4CAF50), "TOPLANMA ALANI")
                    LegendItem(Color(0xFFE91E63), "SAHRA HASTANESİ")
                    LegendItem(Color(0xFF00BCD4), "TEMİZ SU / GIDA")
                }
            }

            // Zoom Control Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { zoomLevel = (zoomLevel * 1.3f).coerceAtMost(5000f) },
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Yakınlaş")
                }
                SmallFloatingActionButton(
                    onClick = { zoomLevel = (zoomLevel / 1.3f).coerceAtLeast(400f) },
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Uzaklaş")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Pre-loaded & Active Emergency Locations List
        Text("Yakındaki Güvenli Bölge ve Toplanma Alanları:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(assemblyPoints) { pt ->
                val distKm = currentLocation?.let { calculateDistanceKm(it.latitude, it.longitude, pt.latitude, pt.longitude) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when (pt.type) {
                                        PointType.ASSEMBLY_AREA -> Color(0xFF4CAF50)
                                        PointType.MEDICAL_TENT -> Color(0xFFE91E63)
                                        PointType.WATER_SUPPLY -> Color(0xFF00BCD4)
                                        else -> Color(0xFFFF9800)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (pt.type) {
                                    PointType.ASSEMBLY_AREA -> Icons.Default.Groups
                                    PointType.MEDICAL_TENT -> Icons.Default.LocalHospital
                                    PointType.WATER_SUPPLY -> Icons.Default.WaterDrop
                                    else -> Icons.Default.Place
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(pt.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(pt.description, fontSize = 11.sp, color = Color.Gray)
                        }

                        distKm?.let {
                            Text(
                                String.format(java.util.Locale.US, "%.2f km", it),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF2196F3)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var ptName by remember { mutableStateOf("") }
        var ptDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Afet Noktası Ekle & Paylaş", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mevcut GPS konumunuza bir toplanma veya yardım noktası ekleyin:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = ptName,
                        onValueChange = { ptName = it },
                        label = { Text("Nokta Adı (Örn: Çadır Alanı)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ptDesc,
                        onValueChange = { ptDesc = it },
                        label = { Text("Açıklama (Örn: Su tankeri mevcut)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ptName.isNotBlank() && currentLocation != null) {
                            val newPt = DisasterPoint(
                                id = "CUSTOM_" + System.currentTimeMillis(),
                                name = ptName,
                                type = PointType.ASSEMBLY_AREA,
                                latitude = currentLocation.latitude,
                                longitude = currentLocation.longitude,
                                description = ptDesc
                            )
                            onAddCustomPoint(newPt)
                        }
                        showAddDialog = false
                    }
                ) {
                    Text("Kaydet ve Ağda Paylaş")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("İptal") }
            }
        )
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

// Haversine formula for calculating distance in km between 2 lat/lon coordinates
fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
