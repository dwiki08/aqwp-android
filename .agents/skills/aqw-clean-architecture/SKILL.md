---
name: aqw-clean-architecture
description: Guides the Clean Architecture refactoring of the AQW Android codebase into UI, Domain, and Data layers. Use when reorganizing packages, decoupling bot logic from sessions, dismantling BotHelper, and implementing modern repository patterns.
---

# AQW Clean Architecture Refactoring Skill

This skill provides architectural guidelines, rules, and procedures for refactoring the AQW Android
application into a 3-layer Clean Architecture (`UI`, `Domain`, `Data`).

---

## 1. Architectural Layers & Dependency Rule

```
[ UI Layer ] (Screens, Components, ViewModels)
     │
     ▼ depends on
[ Domain Layer ] (Coordinators, Business Logic, Domain Models, Repository Interfaces)
     ▲
     │ implemented by
[ Data Layer ] (AqwSession, Network Sockets, Packet Parser, Storage/Repositories)
```

### Dependency Rules:

1. **UI Layer** depends only on **Domain Layer** (Coordinators, UseCases, Models) and **Repository
   Interfaces**.
2. **Domain Layer** has **ZERO** dependencies on Android UI (no Compose, no ViewModels) or low-level
   network sockets.
3. **Data Layer** implements Domain repository interfaces and manages network connections (
   `AqwSocketClient`, `AqwHttpApi`), protocol parsing (`AqwPacketParser`), and disk persistence.

---

## 2. Package Structure Blueprint

```
froztt13.python.aqw/
├── ui/
│   ├── screens/                # Jetpack Compose Screens
│   ├── components/             # Reusable UI widgets
│   ├── theme/                  # Color, Type, Shape
│   └── viewmodel/              # Android ViewModels
├── domain/
│   ├── model/                  # Domain entities (PartyStats, SlotTelemetry, TauntInfo)
│   ├── coordinator/            # Bot Engines (BasePartyCoordinator, EclipseCoordinator, TempleCoordinator)
│   └── repository/             # Repository interfaces (ConfigRepository, SessionRepository)
├── data/
│   ├── network/                # Raw TCP Socket client & Artix HTTP login
│   ├── engine/                 # AqwSession, AqwPacketParser, Command handlers
│   │   └── commands/           # Combat, Movement, Quest, Item, Social commands
│   ├── repository/             # Repository implementations (ConfigRepositoryImpl)
│   └── util/                   # String extensions, ANSI strippers, formatters
└── service/
    └── BotForegroundService.kt # Android Foreground Service keep-alive
```

---

## 3. Four-Phase Execution Workflow

### Phase 1: Data & Persistence Layer Foundation

- Create `ConfigRepository` interface in `domain/repository` and implementation in
  `data/repository`.
- Move JSON file read/write and serialization out of `BotHelper`.
- Create string utility extensions (`stripAnsi()`) in `data/util`.
- Keep `BotHelper` as a backward-compatible delegation facade during migration.
- Verify: `./gradlew compileDebugKotlin`.

### Phase 2: Engine & Network Consolidation (`data/`)

- Relocate low-level networking and session engine:
    - `core/network` -> `data/network`
    - `core/engine` -> `data/engine`
- Remove global static state from `AqwSession` companion object.
- Verify: `./gradlew compileDebugKotlin`.

### Phase 3: Domain Coordinators & Abstraction (`domain/`)

- Create `BasePartyCoordinator` for shared party lifecycle (slot tracking, pause/resume, party
  stats, per-slot log collection).
- Refactor `NativeEclipseBot` -> `EclipseCoordinator`.
- Refactor `NativeTempleBot` -> `TempleCoordinator`.
- Verify: `./gradlew compileDebugKotlin`.

### Phase 4: UI & ViewModel Alignment (`ui/`)

- Update `EclipseViewModel`, `TempleViewModel`, etc. to inject/consume `Coordinator` and
  `ConfigRepository`.
- Retire obsolete code in `BotHelper`.
- Verify: `./gradlew compileDebugKotlin` and verify full application builds.
