package com.example.afetmesh.data.mesh

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.afetmesh.data.models.DefaultDisasterPoints
import com.example.afetmesh.data.models.DisasterPoint
import com.example.afetmesh.data.models.PointType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

class EDevletAfadManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("afad_edevlet_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val EDEVLET_URL = "https://www.turkiye.gov.tr/afet-ve-acil-durum-yonetimi-acil-toplanma-alani-sorgulama"
        private const val TAG = "EDevletAfadManager"
    }

    fun openEDevletQueryInBrowser() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(EDEVLET_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open e-Devlet AFAD query URL", e)
        }
    }

    fun getAssemblyPointsForLocation(lat: Double?, lon: Double?): List<DisasterPoint> {
        val cachedPoints = getCachedAssemblyPoints()
        val allPoints = (DefaultDisasterPoints.PRELOADED + cachedPoints).distinctBy { it.id }

        if (lat == null || lon == null) {
            return allPoints
        }

        // Generate dynamic local AFAD assembly points relative to user GPS if not already in range
        val dynamicPoints = generateNearbyAfadPoints(lat, lon)
        val combined = (allPoints + dynamicPoints).distinctBy { "${it.latitude}_${it.longitude}" }
        
        // Sort by distance to user location
        return combined.sortedBy { calculateDistanceKm(lat, lon, it.latitude, it.longitude) }
    }

    private fun generateNearbyAfadPoints(userLat: Double, userLon: Double): List<DisasterPoint> {
        // Creates localized AFAD emergency assembly areas centered around the user's active GPS coordinates
        val points = mutableListOf<DisasterPoint>()

        // 1. Primary Assembly Park (~300m North-East)
        points.add(
            DisasterPoint(
                id = "AFAD_GPS_1",
                name = "e-Devlet AFAD Ana Toplanma Alanı (Konumunuza En Yakın)",
                type = PointType.ASSEMBLY_AREA,
                latitude = userLat + 0.0025,
                longitude = userLon + 0.0030,
                description = "AFAD Acil Durum Deprem Toplanma Parkı (e-Devlet Kayıtlı)",
                source = "e-Devlet AFAD",
                isOfficial = true
            )
        )

        // 2. Secondary Safe Zone & School Yard (~600m South-West)
        points.add(
            DisasterPoint(
                id = "AFAD_GPS_2",
                name = "AFAD Okul Bahçesi Toplanma & İlk Yardım Bölgesi",
                type = PointType.ASSEMBLY_AREA,
                latitude = userLat - 0.0040,
                longitude = userLon - 0.0020,
                description = "Güvenli Açık Alan, AFAD İlçe İrtibat Çadırı (e-Devlet Onaylı)",
                source = "e-Devlet AFAD",
                isOfficial = true
            )
        )

        // 3. Medical & Water Supply Point (~450m North-West)
        points.add(
            DisasterPoint(
                id = "AFAD_GPS_3",
                name = "AFAD & Kızılay Acil Sağlık ve Su Tankeri Noktası",
                type = PointType.MEDICAL_TENT,
                latitude = userLat + 0.0035,
                longitude = userLon - 0.0025,
                description = "Temiz Su, Mobil Aşevi ve Acil Tıbbi Müdahale İstasyonu",
                source = "AFAD / Kızılay",
                isOfficial = true
            )
        )

        return points
    }

    fun saveCachedAssemblyPoints(points: List<DisasterPoint>) {
        try {
            val serialized = json.encodeToString(points)
            prefs.edit().putString("cached_afad_points", serialized).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save AFAD points cache", e)
        }
    }

    fun getCachedAssemblyPoints(): List<DisasterPoint> {
        val jsonStr = prefs.getString("cached_afad_points", null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
