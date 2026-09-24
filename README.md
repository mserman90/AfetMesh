# 🚨 AfetMesh - Off-Grid Afet Haberleşme Sistemi (Android + Windows PC)

**AfetMesh**, deprem, doğal afet ve şebeke kesintisi durumlarında **internet, hücresel veri veya baz istasyonu olmadan** civardaki Android telefonlar ve Windows bilgisayarlar arasında **Sesli (Telsiz/PTT), Yazılı (Mesh Chat) ve Görüntülü (P2P Canlı Yayın)** haberleşmeyi sağlayan Briar esintili acil durum haberleşme platformudur.

---

## 📲💻 İndirme Bağlantıları (Android & Windows PC)

| Platform | Format | İndirme Bağlantısı |
| :--- | :--- | :--- |
| **📱 Android Telefonlar** | `.apk` | **[AfetMesh-v1.0.apk İndir](https://github.com/mserman90/AfetMesh/releases/download/v1.0/AfetMesh-v1.0.apk)** |
| **💻 Windows Bilgisayarlar** | `.exe` | **[AfetMesh_Desktop-v1.0.exe İndir](https://github.com/mserman90/AfetMesh/releases/download/v1.0/AfetMesh_Desktop-v1.0.exe)** |

---

## ✨ Öne Çıkan Özellikler

### 🚨 1. ACİL SOS & Arama-Kurtarma Beaconi
- **Tek Dokunuşla SOS Yayını**: *Enkaz Altındayım, Yaralıyım, Acil Yardım Lazım, Güvendeyim, Su/Yiyecek Lazım* durumları anında hem telefonlara hem bilgisayara yayınlanır.
- **Yüksek Desibel Siren & Alarm**: PC hoparlörlerinden ve telefondan siren sesi çalar.
- **Mors Kodu Flaşör (SOS Strobe)**: Telefon kamerasının flaşı üzerinden `... --- ...` (S.O.S) mors kodu ışıklı işaretçi yakar.

### 💬 2. Yazılı Mesh Chat (Briar Store-and-Forward)
- **Çapraz Platform İnternetsiz Mesajlaşma**: Telefonlar ve Bilgisayar aynı Wi-Fi / Hotspot ağında milisaniyeler içinde eşleşir.
- **Çoklu Sıçrama (Mesh Relay)**: Mesajlar, kapsama alanı dışındaki kişilere ulaşabilmek için yakındaki diğer cihazlar üzerinden sıçrayarak (relay) iletilir.

### 🎙️ 3. Sesli Haberleşme (PTT - Telsiz / Walkie-Talkie)
- **Bas-Konuş (Push-to-Talk)**: Butona basılı tutarak bilgisayar mikrofona veya telefona konuşun. Canlı ses tüm bağlı cihazların hoparlöründen telsiz gibi dinlenir.

### 📹 4. Görüntülü Haberleşme (P2P Canlı Video Akışı)
- **Kamera Yayınlama & İzleme**: Telefon veya bilgisayar kamerasından internetsiz canlı video yayını yapın, diğer cihazların ekranından canlı izleyin.

### 📡 5. Mesh Radarı & Cihaz Tespiti
- Çevredeki tüm aktif Android ve PC düğümlerini, IP adreslerini, pil yüzdelerini ve SOS durumlarını canlı radar üzerinde gösterir.

---

## 🛠️ Afet Anında Nasıl Kullanılır? (Off-Grid Bağlantı)

İnternet ve baz istasyonları tamamen kapalı olsa dahi cihazlar arası bağlantı kurmak için:

1. **Yöntem A (Ortak Wi-Fi / Taşınabilir Modem)**: Bilgisayarı ve telefonları aynı Wi-Fi ağına (internet olmasa dahi çalışan bir modem veya şarjlı taşınabilir modem) bağlayın.
2. **Yöntem B (Kişisel Erişim Noktası / Hotspot)**: Telefonda **Kişisel Erişim Noktası (Hotspot)** açın (hücresel veri kapalı olsa da çalışır). Bilgisayarı bu Wi-Fi ağına bağlayın.
3. **AfetMesh** (Android veya PC uygulaması) otomatik olarak aynı ağdaki tüm cihazları keşfeder ve haberleşmeye başlar.

---

## 🏗️ Proje Mimarisi

- **Android**: Kotlin, Jetpack Compose (Material 3), CameraX, AudioRecord/AudioTrack
- **Windows Masaüstü**: Python (Tkinter GUI, SoundDevice, OpenCV, Pillow)
- **Ağ Motoru**: UDP Multicast Discovery Beacon (8888) + Local TCP Socket Mesh Engine (8889)

```bash
# Masaüstü Uygulamasını Kaynak Koddan Çalıştırmak İçin:
python desktop/afetmesh_desktop.py
```

---

## 📄 Lisans
Bu proje kamu yararı gözetilerek afet ve acil durum haberleşmesi amacıyla açık kaynak olarak geliştirilmiştir.
