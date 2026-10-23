# Virtual Bluetooth Gamepad (PS5 DualSense Edition)

Aplikasi Android performa tinggi yang mengubah ponsel Android menjadi gamepad Bluetooth virtual standar (Human Interface Device / HID) berdesain ergonomis ala Sony PlayStation 5 DualSense, dapat langsung terhubung ke PC (Windows/Linux/Mac), Android TV, dan konsol tanpa aplikasi server tambahan.

## User Review & Critical Decisions

> [!IMPORTANT]
> Konfirmasi preferensi pengguna telah diintegrasikan:
> - **Gaya Visual**: DualSense Classic (Nuansa Putih Matte, Center Cradle Hitam, dengan Aksen Neon Lightbar Biru/Cyan bercahaya).
> - **Konektivitas**: Mode Discoverable Otomatis dengan Quick Pairing Bar di sisi atas layar untuk pemindaian dan status koneksi instan.
> - **Respon Haptik**: Haptic Mikro Ringan menggunakan `VibrationEffect` instan (15ms / `EFFECT_CLICK`) untuk feedback mekanis tanpa latency sentuh.

---

## 1. Overview & Core Concept

- **Apa Fungsinya**: Menggunakan Android `BluetoothHidDevice` API bawaan untuk mendaftarkan ponsel sebagai perangkat keras HID Gamepad standar. Host (PC, Android TV, konsol) mendeteksi HP sebagai joystick/gamepad fisik asli plug-and-play.
- **Target Pengguna**: Gamer yang membutuhkan controller cadangan atau portabel untuk bermain game di PC/Laptop, Steam Deck, Android TV, atau konsol kapan pun dibutuhkan.
- **Nilai Utama**: Nol aplikasi receiver/server pihak ketiga di PC, latency input sub-frame ultra rendah, alokasi memori statis (zero-allocation) pada touch events untuk mencegah stutter akibat garbage collector, serta layout sentuh intuitif yang memetakan seluruh kontrol PS5 DualSense.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Inisialisasi & Izin**: Saat aplikasi dibuka dalam orientasi landscape penuh (edge-to-edge immersive), aplikasi memeriksa izin Bluetooth (Android 12+ `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`) dan status adapter Bluetooth.
2. **Status Koneksi & Quick Pairing**: Header status ramping menampilkan indikator koneksi HID ("Menunggu Host...", "Discoverable", atau "Terhubung ke [Nama PC/Host]"). Terdapat tombol cepat "Make Discoverable" dan tombol pemutus koneksi.
3. **Pengoperasian Gamepad**:
   - **Sisi Kiri**: Tombol bahu L1 & L2 (trigger analog variabel), 4-arah D-Pad mekanis, Create/Share button, dan Left Thumbstick analog (dengan tombol tekan L3).
   - **Tengah**: Clickable Touchpad dengan visual LED Lightbar bercahaya khas DualSense, tombol PS, tombol Mute mic, dan quick status info.
   - **Sisi Kanan**: Tombol bahu R1 & R2 (trigger analog variabel), 4 tombol aksi geometri klasik (✕, ◯, ◻, △), Options button, dan Right Thumbstick analog (dengan tombol tekan R3).
4. **Haptic & Visual Feedback**: Setiap elemen memberikan efek visual glow neon biru dan impuls haptik mikro saat disentuh.

### Visual Identity & Theme
- **Aesthetic Direction**: Ergonomi futuristik DualSense PS5 dengan presisi tinggi.
- **Palet Warna**:
  - Grip Shell: `#ECEFF1` (Putih abu-abu matte khas DualSense)
  - Center Cradle & Analog Domes: `#121316` / `#1E2024` (Hitam arang bertekstur)
  - DualSense LED Lightbar: `#0080FF` / `#00E5FF` (Glow neon biru dinamis)
  - Action Glyphs: Simbol ✕ (Biru), ◯ (Merah muda), ◻ (Pink cerah), △ (Hijau toska)
  - Status Indicator: Amber (Discoverable), Hijau Neon (Connected), Merah Halus (Disconnected)
- **Desain Touch Target**: Mengikuti standar aksesibilitas minimum 48dp dengan area tangkapan multi-touch luas yang responsif.

---

## 3. Key Product Decisions & Trade-Offs

- **Bluetooth HID Device API vs Server WebSocket/WiFi**:
  - *Pendekatan Terpilih*: Native Android `BluetoothHidDevice` (Bluetooth Profile HID Device).
  - *Alasan*: Host tidak perlu menginstal software apa pun; kompatibel langsung dengan sistem operasi apa pun yang mendukung Gamepad Bluetooth standar.
  - *Alternatif*: TCP/UDP client-server via Wi-Fi membutuhkan software jembatan di PC dan rentan jitter jaringan Wi-Fi.

- **Zero-Allocation Multi-Touch Input Loop**:
  - *Pendekatan Terpilih*: Alokasi statis pada array byte HID Report (`ByteArray(8)`) dan objek kalkulasi analog joystick. Input diproses melalui looping MotionEvent pointer index tanpa instansiasi objek baru di dalam event loop.
  - *Alasan*: Menghindari lonjakan Garbage Collection (GC) Android yang dapat menyebabkan stuttering atau lonjakan latency input saat bermain game intensif.

- **Rendering Layer (Optimized Jetpack Compose + Native DrawScope Canvas)**:
  - *Pendekatan Terpilih*: UI Jetpack Compose dengan `drawWithCache` dan `pointerInput` tingkat rendah untuk tombol, serta Custom Canvas untuk dual-analog sticks dengan dead-zone math dan spring centering.
  - *Alasan*: Menghasilkan visual Material 3 modern 60 FPS halus dengan konsumsi CPU rendah.

---

## 4. Technical Architecture & Data Strategy

```
┌───────────────────────────────────────────────────────────┐
│                      MainActivity                         │
│  (Immersive Landscape, WindowInsets, Permission Launcher) │
└─────────────────────────────┬─────────────────────────────┘
                              │
       ┌──────────────────────┴──────────────────────┐
       ▼                                             ▼
┌───────────────────────────┐       ┌───────────────────────────┐
│     DualSenseScreen       │       │    BluetoothHidManager    │
│  - Quick Pairing Bar      │       │  - BluetoothHidDevice API │
│  - D-Pad & Action Buttons │◄─────►│  - HID Report Descriptor  │
│  - Dual Analog Joysticks  │       │  - SDP App Registration   │
│  - Triggers (L1/L2/R1/R2) │       │  - Non-blocking Dispatch  │
│  - PS / Touchpad Click    │       └─────────────┬─────────────┘
└──────────────┬────────────┘                     │
               │ Input Events                     ▼
               ▼                     ┌──────────────────────────┐
┌───────────────────────────┐        │   Host Device (PC/TV)    │
│     GamepadStateStore     │───────►│  (Detects Standard PS/   │
│  - Static Byte Packing    │ Report │   Generic Bluetooth HID) │
│  - Micro Haptic Feedback  │ Bytes  └──────────────────────────┘
└───────────────────────────┘
```

### HID Report Descriptor Structure
Descriptor standar Gamepad 8-byte:
- **Byte 0**: Tombol Aksi & Bahu (Bit 0: Square, Bit 1: Cross, Bit 2: Circle, Bit 3: Triangle, Bit 4: L1, Bit 5: R1, Bit 6: L2, Bit 7: R2)
- **Byte 1**: Tombol Sistem & Stick Clicks (Bit 0: Create, Bit 1: Options, Bit 2: L3, Bit 3: R3, Bit 4: PS Button, Bit 5: Touchpad Click, Bit 6-7: Reserved)
- **Byte 2**: D-Pad Hat Switch (0 = Up, 1 = Up-Right, 2 = Right, 3 = Down-Right, 4 = Down, 5 = Down-Left, 6 = Left, 7 = Up-Left, 8 = Neutral)
- **Byte 3**: Left Thumbstick X Axis (`-127` s/d `127` mapped to `0..255`)
- **Byte 4**: Left Thumbstick Y Axis (`-127` s/d `127` mapped to `0..255`)
- **Byte 5**: Right Thumbstick Z Axis (`-127` s/d `127` mapped to `0..255`)
- **Byte 6**: Right Thumbstick Rz Axis (`-127` s/d `127` mapped to `0..255`)
- **Byte 7**: Trigger L2 & R2 Pressure Level (`0..255`)

### Berkas Utama yang Akan Dibangun:
1. `metadata.json` & `strings.xml`: Sinkronisasi nama aplikasi ("DualSense HID Gamepad").
2. `AndroidManifest.xml`: Izin Bluetooth Connect/Advertise/Scan/Admin dan konfigurasi landscape fullscreen tanpa sensor orientation lock.
3. `BluetoothHidManager.kt`: Wrapper BluetoothHidDevice, registrasi profil SDP gamepad, callback siklus hidup koneksi, dan fungsi `sendReport` statis yang cepat.
4. `GamepadState.kt`: Model status controller & encoder byte stream non-alokasi.
5. `DualSenseControllerView.kt`: Komponen tata letak DualSense lengkap dengan multi-touch gesture filter, dual analog sticks, tombol aksi geometri, D-pad, shoulder buttons, touchpad, dan visual LED lightbar.
6. `MainActivity.kt`: Controller host binding, dialog izin dinamis, quick pairing action bar, serta integrasi haptik `VibratorManager`.
