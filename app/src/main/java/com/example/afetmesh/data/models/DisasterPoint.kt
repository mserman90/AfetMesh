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
    val source: String = "AFAD / e-Devlet",
    val isOfficial: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

object DefaultDisasterPoints {
    const val EDEVLET_AFAD_URL = "https://www.turkiye.gov.tr/afet-ve-acil-durum-yonetimi-acil-toplanma-alani-sorgulama"

    val PRELOADED = listOf(
        DisasterPoint(
            id = "AFAD_1",
            name = "Fatih Parkı AFAD Acil Toplanma Alanı",
            type = PointType.ASSEMBLY_AREA,
            latitude = 41.0082,
            longitude = 28.9784,
            description = "e-Devlet Onaylı AFAD Acil Toplanma Alanı (KOD: AFAD-34-01)",
            source = "e-Devlet AFAD",
            isOfficial = true
        ),
        DisasterPoint(
            id = "AFAD_2",
            name = "Kızılay Sahra Hastanesi & Tıbbi Müdahale",
            type = PointType.MEDICAL_TENT,
            latitude = 41.0125,
            longitude = 28.9750,
            description = "AFAD & Kızılay Acil Sağlık İrtibat Çadırı",
            source = "AFAD / Kızılay",
            isOfficial = true
        ),
        DisasterPoint(
            id = "AFAD_3",
            name = "Merkez İSKİ Su Tankeri & Aşevi Dağıtım Noktası",
            type = PointType.WATER_SUPPLY,
            latitude = 41.0050,
            longitude = 28.9820,
            description = "Aşevi, İçme Suyu ve Mobil Jeneratör Noktası",
            source = "e-Devlet AFAD",
            isOfficial = true
        ),
        DisasterPoint(
            id = "AFAD_4",
            name = "Gülhane Parkı Açık Güvenli Bölge",
            type = PointType.ASSEMBLY_AREA,
            latitude = 41.0130,
            longitude = 28.9810,
            description = "e-Devlet Onaylı AFAD İkincil Toplanma Alanı (KOD: AFAD-34-02)",
            source = "e-Devlet AFAD",
            isOfficial = true
        ),
        DisasterPoint(
            id = "AFAD_5",
            name = "Yenikapı Etkinlik Alanı Ana Deprem Kriz Merkezi",
            type = PointType.ASSEMBLY_AREA,
            latitude = 41.0020,
            longitude = 28.9540,
            description = "e-Devlet AFAD Bölgesel Ana Toplanma ve Çadır Kent Alanı",
            source = "e-Devlet AFAD",
            isOfficial = true
        )
    )
}
