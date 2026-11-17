# 🎵 BitChord Desktop (Linux & Windows)

BitChord Desktop adalah klien YouTube Music native untuk platform desktop (**Linux & Windows**) yang dirancang menggunakan **Compose Multiplatform** dengan performa tinggi, tampilan estetis bernuansa *frosted-glass*, dan integrasi mendalam dengan sistem operasi.

---

## 🌟 Fitur Utama Desktop

### 1. Integrasi Sistem Operasi Native
- **Linux MPRIS & Media Keys**: Terhubung langsung dengan panel media Linux (GNOME Media, KDE Plasma Media Player, `playerctl`, dan tombol multimedia keyboard).
- **Windows SMTC**: Integrasi kontrol media Windows (System Media Transport Controls) di taskbar dan lock screen.
- **System Tray**: Dukungan tray modern (`org.kde.StatusNotifierItem` untuk KDE/GNOME dan fallback AWT Tray di Windows/Linux).
- **Desktop Entry Linux**: Icon resolusi tinggi dan integrasi launcher sistem (`bitchord.desktop`) dengan menu context Play/Pause, Next, dan Prev.

### 2. Discord Rich Presence (RPC) Native
- Terhubung secara otomatis ke aplikasi Discord desktop tanpa memerlukan bot token atau login akun pihak ketiga melalui socket lokal IPC (**Unix Domain Sockets** di Linux dan **Named Pipes** di Windows).
- Menampilkan judul lagu, artis, cover album, dan durasi berjalan secara real-time.

### 3. Audio Engine & Lirik Real-Time
- **FFmpeg Decoding (JavaCPP)**: Mendukung format Lossless Hi-Res (FLAC, ALAC, WAV) serta format streaming standar (Opus, AAC, MP3, WebM).
- **Windowed Streaming (`googlevideo.com`)**: Mengatasi masalah error HTTP 403 Google Video dengan mekanisme range requests cerdas.
- **PoToken Headless Generator**: Menggunakan WebEngine JavaFX WebKit untuk memproses JavaScript BotGuard Google tanpa membuka jendela browser.
- **Lirik Kata-demi-Kata (*Word-by-Word*)**: Animasi lirik per-frame bergaya Apple Music dengan multi-sumber lirik (LRCLib, Musixmatch, YouTube Captions, KuGou, dll.) dan dukungan lirik tertanam (*embedded lyrics*) dari file lagu lokal.

### 4. In-App Updater & Info Sistem
- Halaman dedicated **Pembaruan & Info Sistem** di menu Settings:
  - Otomatis mendeteksi sistem operasi, arsitektur CPU, dan runtime Java.
  - Otomatis mendeteksi metode instalasi (AppImage, DEB, RPM, Portable, dll.).
  - Tombol pemeriksaan pembaruan manual langsung dari repository GitHub `efzynx/BitChord-Desktop`.

---

## ⌨️ Daftar Shortcut Keyboard

| Shortcut | Fungsi |
| :--- | :--- |
| **`Spasi`** | Putar / Jeda (*Play / Pause*) |
| **`Tombol Media Play/Pause`** | Putar / Jeda dari keyboard multimedia |
| **`Tombol Media Next / Prev`** | Lagu berikutnya / lagu sebelumnya |
| **`Panah Kiri` (`←`)** | Mundur 5 detik |
| **`Panah Kanan` (`→`)** | Maju 5 detik |
| **`Panah Atas` (`↑`)** | Naikkan volume (+5%) |
| **`Panah Bawah` (`↓`)** | Turunkan volume (-5%) |
| **`M`** | Bisukan / Nyalakan suara (*Mute / Unmute*) |
| **`Ctrl + F`** / **`Cmd + F`** | Langsung fokus ke kolom pencarian (*Search*) |
| **`Ctrl + ,`** / **`Cmd + ,`** | Buka halaman Pengaturan (*Settings*) |
| **`F11`** atau **`F`** | Masuk / Keluar mode Layar Penuh (*Fullscreen*) |
| **`Esc`** | Menutup overlay Now Playing / Dialog / Kembali |

---

## 📥 Panduan Instalasi

### 🐧 Linux

#### 1. AppImage (Direkomendasikan - Universal untuk Semua Distro)
1. Unduh file `BitChord-*.AppImage` dari halaman [Releases](https://github.com/efzynx/BitChord-Desktop/releases).
2. Berikan izin eksekusi:
   ```bash
   chmod +x BitChord-*.AppImage
   ./BitChord-*.AppImage
   ```

#### 2. Paket Debian / Ubuntu / Linux Mint (`.deb`)
```bash
sudo dpkg -i BitChord_*_amd64.deb
sudo apt-get install -f # Jika memerlukan dependensi sistem
```

#### 3. Paket Fedora / RHEL / openSUSE (`.rpm`)
```bash
sudo rpm -i BitChord-*.rpm
# Atau via dnf
sudo dnf install ./BitChord-*.rpm
```

#### 4. Portable Tar.gz / Instalasi Lokal via Script
Jika Anda mengunduh paket tar.gz atau mengompilasi dari source code, Anda dapat menggunakan script instalasi praktis yang disediakan:
```bash
# Menjalankan installer (otomatis mendaftarkan ke Menu Aplikasi Linux & path bin):
./scripts/install-desktop.sh

# Menghapus instalasi:
./scripts/uninstall-desktop.sh
```

---

### 🪟 Windows

#### 1. Windows Installer (`.msi` / `.exe`)
1. Unduh installer `BitChord-*.msi` atau `BitChord-*.exe`.
2. Jalankan installer dan ikuti petunjuk di layar. Pintasan di Start Menu dan Desktop akan otomatis dibuat.

#### 2. Windows Portable (`.zip`)
1. Unduh arsip `BitChord-*-windows-portable.zip`.
2. Ekstrak folder `BitChord` ke lokasi mana saja (misalnya `D:\Games\BitChord` atau folder Dokumen Anda).
3. Jalankan **`BitChord.vbs`** (untuk menjalankan aplikasi secara hening tanpa pop-up jendela hitam Command Prompt).
4. Anda juga dapat membuat shortcut ke `BitChord.vbs` di Desktop.

---

## 🛠️ Kompilasi dari Source Code

### Prasyarat
- **Java JDK 17 atau 21** (direkomendasikan Eclipse Temurin atau OpenJDK).

### Langkah Build untuk Linux:
```bash
# 1. Build Standalone Linux Executable:
./gradlew :desktopApp:createDistributable

# Hasil ada di:
# desktopApp/build/compose/binaries/main/app/BitChord/bin/BitChord

# 2. Build Paket .deb dan .rpm (memerlukan fakeroot & dpkg-dev/rpm):
./gradlew :desktopApp:packageDeb :desktopApp:packageRpm

# 3. Menjalankan seluruh test suite:
./gradlew :desktopApp:test
```

### Langkah Build untuk Windows:
```bash
# Build paket Windows Portable dari Linux atau Windows:
./gradlew :desktopApp:windowsPortable -Pbitchord.target=windows

# Hasil ada di:
# desktopApp/build/distributions/BitChord-*-windows-portable.zip
```
