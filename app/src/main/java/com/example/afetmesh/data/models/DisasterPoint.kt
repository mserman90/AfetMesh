package com.example.afetmesh.data.models

import kotlinx.serialization.Serializable

enum class PointType {
    ASSEMBLY_AREA,     // Toplanma Alanı
    MEDICAL_TENT,      // Sahra Hastanesi / Tıbbi Çadır
    WATER_SUPPLY,      // Temiz Su Dağıtım Noktası
    FOOD_SUPPLY,       // Gıda & Aşevi
    SOS_LOCATION       // Acil SOS Çağrısı Konumu
}

@Serializable
data class DisasterPoint(
    val id: String,
    val name: String,
    val type: PointType,
    val latitude: Double,
    val longitude: Double,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object DefaultDisasterPoints {
    val PRELOADED = listOf(
        DisasterPoint(
            id = "DEF_1",
            name = "Merkez Afet Toplanma Alanı & Çadır Kent",
            type = PointType.ASSEMBLY_AREA,
            latitude = 41.0082,
            longitude = 28.9784,
            description = "AFAD Ana Kriz Masası, Çadır Kent ve İnsani Yardım Dağıtımı"
        ),
        DisasterPoint(
            id = "DEF_2",
            name = "Sahra Hastanesi & İlk Yardım Çadırı",
            type = PointType.MEDICAL_TENT,
            latitude = 41.0125,
            longitude = 28.9750,
            description = "Kızılay Sahra Hastanesi, Acil Müdahale ve Ambulans Noktası"
        ),
        DisasterPoint(
            id = "DEF_3",
            name = "Temiz Su Tankeri & Gıda Aşevi",
            type = PointType.WATER_SUPPLY,
            latitude = 41.0050,
            longitude = 28.9820,
            description = "İSKİ Temiz Su Tankeri ve Sıcak Çorba Dağıtımı"
        ),
        DisasterPoint(
            id = "DEF_4",
            name = "İkinci Şehir Parkı Toplanma Alanı",
            type = PointType.ASSEMBLY_AREA,
            latitude = 41.0150,
            longitude = 28.9850,
            description = "Açık Güvenli Bölge, Yangın ve Artçı Deprem Güvenlik Alanı"
        )
    )
}
