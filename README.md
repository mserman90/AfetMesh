# 🚨 AfetMesh - Off-Grid Afet Haberleşme Uygulaması

**AfetMesh**, deprem, doğal afet ve şebeke kesintisi durumlarında **internet, hücresel veri veya baz istasyonu olmadan** civardaki Android telefonlar arasında **Sesli (Telsiz/PTT), Yazılı (Mesh Chat) ve Görüntülü (P2P Canlı Yayın)** haberleşmeyi sağlayan Briar esintili acil durum uygulamasıdır.

---

## 📲 APK Doğrudan İndirme ve Kurulum (Diğer Telefonlar İçin)

İnternet erişiminiz varken veya cihazdan cihaza (Bluetooth / Wi-Fi Direct / Dosya Paylaşımı ile) APK'yı diğer telefonlara aktarıp hemen kurabilirsiniz:

1. **[AfetMesh-v1.0.apk İndir (GitHub Release)](https://github.com/mserman90/AfetMesh/releases/latest/download/AfetMesh-v1.0.apk)**
2. İndirdiğiniz `AfetMesh-v1.0.apk` dosyasına dokunun.
3. Telefonunuz *"Bilinmeyen kaynaklardan yükleme"* uyarısı verirse **İzin Ver / Yükle** seçeneğini kabul edin.
4. Uygulamayı açtığınızda mikrofon, kamera ve konum izinlerini onaylayın.

---

## ✨ Öne Çıkan Özellikler

### 🚨 1. ACİL SOS & Arama-Kurtarma Beaconi
- **Tek Dokunuşla SOS Yayını**: *Enkaz Altındayım, Yaralıyım, Acil Yardım Lazım, Güvendeyim, Su/Yiyecek Lazım* gibi durumlarla anında tüm yakındaki telefonlara acil durum uyarısı yayınlar.
- **Yüksek Desibel Siren**: Enkaz altında arama-kurtarma ekiplerinin sizi bulabilmesi için yüksek sesli alarm çalar.
- **Mors Kodu Flaşör (SOS Strobe)**: Telefon kamerasının flaşı üzerinden `... --- ...` (S.O.S) mors kodu ışıklı işaretçi yakar.

### 💬 2. Yazılı Mesh Chat (Briar Store-and-Forward)
- **İnternetsiz P2P Mesajlaşma**: Aynı Wi-Fi / Hotspot kapsama alanındaki tüm cihazlar otomatik eşleşir.
- **Çoklu Sıçrama (Mesh Relay)**: Mesajlar, kapsama alanı dışındaki kişilere ulaşabilmek için yakındaki diğer telefonlar üzerinden sıçrayarak (relay) iletilir.
- **Hızlı Şablonlar & Sesli Not**: Dokunarak hazır afet mesajları veya kısa ses kayıtları gönderme.

### 🎙️ 3. Sesli Haberleşme (PTT - Telsiz / Walkie-Talkie)
- **Bas-Konuş (Push-to-Talk)**: Butona basılı tutarak düşük gecikmeli PCM ses yayını yapma. Telsiz mantığıyla çevredeki tüm bağlı telefonlara ses canlı aktarılır.

### 📹 4. Görüntülü Haberleşme (P2P Canlı Video Akışı)
- **İnternetsiz Canlı Video**: Kamera görüntüsü sıkıştırılarak local TCP soketi üzerinden yakındaki telefonun ekranına canlı görüntü olarak aktarılır.

### 📡 5. Mesh Radarı & Cihaz Tespiti
- Çevredeki tüm aktif cihazları, IP adreslerini, pil yüzdelerini ve SOS durumlarını canlı radar üzerinde gösterir.

---

## 🛠️ Afet Anında Nasıl Kullanılır? (Off-Grid Bağlantı)

İnternet ve baz istasyonları tamamen kapalı olsa dahi telefonlar arası bağlantı kurmak için:

1. **Yöntem A (Ortak Wi-Fi / Taşınabilir Modem)**: Telefonların hepsini herhangi bir Wi-Fi ağına (internet olmasa dahi çalışan bir modem veya şarjlı taşınabilir modem) bağlayın.
2. **Yöntem B (Kişisel Erişim Noktası / Hotspot)**: Bir telefon **Kişisel Erişim Noktası (Hotspot)** açar (hücresel veri kapalı olsa da çalışır). Diğer telefonlar bu Wi-Fi ağına bağlanır.
3. **AfetMesh** otomatik olarak aynı ağdaki tüm cihazları milisaniyeler içinde keşfeder ve haberleşmeye başlar.

---

## 🏗️ Geliştiriciler İçin Derleme

- **Dil**: Kotlin
- **Arayüz**: Jetpack Compose (Material 3)
- **Minimum Android**: API 23 (Android 6.0 Marshmallow ve üzeri)
- **Ağ Motoru**: UDP Multicast Beacon + Local TCP Socket Mesh Engine

```bash
git clone https://github.com/mserman90/AfetMesh.git
cd AfetMesh
./gradlew assembleDebug
```

---

## 📄 Lisans
Bu proje kamu yararı gözetilerek afet ve acil durum haberleşmesi amacıyla açık kaynak olarak geliştirilmiştir.
