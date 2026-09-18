# Clean Architecture Refactoring Plan: AQW Android

Dokumen ini adalah rencana kerja komprehensif untuk merestrukturisasi codebase **AQW Android Bot**
ke dalam arsitektur berlapis **UI - Domain - Data** (Clean Architecture).

---

## 1. Tujuan Refactoring

1. **Separation of Concerns**: Memisahkan antarmuka (UI), aturan main game/raid (Domain), dan
   koneksi jaringan/penyimpanan (Data).
2. **Dismantle God Object `BotHelper`**: Memecah `BotHelper` (900+ baris) menjadi `ConfigRepository`
   murni dan ekstensi utility.
3. **Eliminasi Ketergantungan Singleton Statis**: Mengubah bot yang awalnya `object` kaku menjadi
   arsitektur `class` berbasis `BasePartyCoordinator`.
4. **Isolasi Log & State**: Menjamin setiap sesi akun memiliki log dan state terisolasi tanpa
   interferensi global static state.
5. **Zero Downtime Migration**: Setiap fase harus dapat dikompilasi dengan sukses (
   `BUILD SUCCESSFUL`) tanpa merusak fungsionalitas yang ada.

---

## 2. Blueprint Lapisan & Struktur Folder

```
froztt13.python.aqw/
├── ui/                                 # [UI LAYER]
│   ├── screens/                        # Jetpack Compose Screens
│   ├── components/                     # Reusable UI widgets
│   ├── theme/                          # Theme styles
│   └── viewmodel/                      # ViewModels (UI state holders)
├── domain/                             # [DOMAIN LAYER]
│   ├── model/                          # Domain models (PartyStats, SlotTelemetry, TauntInfo)
│   ├── coordinator/                    # Bot Engines (BasePartyCoordinator, EclipseCoordinator, etc.)
│   └── repository/                     # Repository interfaces (ConfigRepository, SessionRepository)
├── data/                               # [DATA LAYER]
│   ├── network/                        # Sockets (AqwSocketClient) & HTTP (AqwHttpApi)
│   ├── engine/                         # AqwSession, AqwPacketParser, Commands
│   │   └── commands/                   # Combat, Movement, Quest, Item, Social
│   ├── repository/                     # Repository implementations (ConfigRepositoryImpl)
│   └── util/                           # StringExtensions (stripAnsi, formatters)
└── service/                            # [ANDROID SERVICE]
    └── BotForegroundService.kt         # Foreground Service keep-alive
```

---

## 3. Fase Eksekusi Bertahap

### 📌 Fase 1: Data & Persistence Layer Foundation (Fokus Saat Ini)

- [ ] Buat interface `domain/repository/ConfigRepository.kt`.
- [ ] Buat `data/util/StringExtensions.kt` (pindahkan fungsi `stripAnsi` dari `BotHelper`).
- [ ] Buat implementasi `data/repository/ConfigRepositoryImpl.kt` untuk menyimpan, memuat, dan
  me-reset file JSON konfigurasi:
    - `eclipse_config.json`
    - `temple_config.json`
    - `doom_config.json`
    - `slavery_config.json`
    - `general_config.json`
- [ ] Pindahkan logika serialisasi & parsing manual JSON dari `BotHelper` ke `ConfigRepositoryImpl`.
- [ ] Ubah `BotHelper` menjadi *facade* yang mendelegasikan panggilan ke `ConfigRepositoryImpl`
  untuk backward compatibility.
- [ ] Verifikasi kompilasi: `./gradlew compileDebugKotlin`.

### 📌 Fase 2: Engine & Network Consolidation (`data/`)

- [ ] Pindahkan `core/network` ke `data/network`:
    - `AqwSocketClient.kt`
    - `AqwHttpApi.kt`
- [ ] Pindahkan `core/engine` ke `data/engine`:
    - `AqwSession.kt`
    - `AqwPacketParser.kt`
    - `commands/*`
- [ ] Bersihkan `companion object` di `AqwSession` yang memegang `_activeSessions` statis.
- [ ] Verifikasi kompilasi: `./gradlew compileDebugKotlin`.

### 📌 Fase 3: Domain Coordinators & Abstraction (`domain/`)

- [ ] Buat kelas dasar `domain/coordinator/BasePartyCoordinator.kt`:
    - Mengelola slot party (Slot 1–4).
    - Mengelola lifecycle (start, pause, resume, stop).
    - Mengumpulkan telemetri dan log per slot secara otomatis.
- [ ] Refaktor `NativeEclipseBot` menjadi `EclipseCoordinator` yang mewarisi `BasePartyCoordinator`.
- [ ] Refaktor `NativeTempleBot` menjadi `TempleCoordinator` yang mewarisi `BasePartyCoordinator`.
- [ ] Sediakan adapter singleton sementara jika diperlukan untuk mempertahankan kompatibilitas UI
  sebelum Fase 4.
- [ ] Verifikasi kompilasi: `./gradlew compileDebugKotlin`.

### 📌 Fase 4: UI & ViewModel Alignment (`ui/`)

- [ ] Perbarui `EclipseViewModel` agar mengonsumsi `EclipseCoordinator` dan `ConfigRepository`.
- [ ] Perbarui `TempleViewModel` agar mengonsumsi `TempleCoordinator` dan `ConfigRepository`.
- [ ] Hapus method dan field deprecated/usang di `BotHelper`.
- [ ] Uji coba build menyeluruh: `./gradlew compileDebugKotlin` & `./gradlew test`.
