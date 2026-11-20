# Pulse

A wearables-agnostic, WHOOP-style health-fitness app for Android. Pulse does **not**
ship its own hardware: it reads data from the devices you already have (Garmin,
Amazfit/Zepp, or any Health Connect producer) and computes every score **locally, on
your phone**.

> ⚠️ **Pulse is not a medical device.** All scores are informational wellness
> estimates. Nothing here diagnoses, treats, or prevents any condition. See
> [docs/PRIVACY.md](docs/PRIVACY.md).

## Features

- **Recovery (0–100)** — HRV (ln RMSSD), resting HR, respiratory rate, SpO₂, skin
  temperature and sleep performance, z-scored against your personal 30-day baseline
  and combined into a single score with a per-input contribution breakdown.
- **Strain (0–21)** — Banister TRIMP over the whole physiological day, sex-specific
  factors, plus a separate Muscular Load for strength sessions.
- **Sleep** — hypnogram, stage breakdown, sleep-need model (base + strain addition +
  capped debt − naps), performance / consistency / efficiency, and a bedtime planner.
- **Stress (0–3)** — 5-minute windows outside workouts and sleep, HR vs personal
  resting baseline, escalation on depressed HRV.
- **Journal** — 20+ built-in behaviors plus custom ones; after ≥5 samples with and
  ≥5 without a behavior, next-day recovery impact is computed with Welch's t-test
  and reported as *emerging / likely / strong*.
- **Trends** — 7/30/90/365-day charts, weekly and monthly reports.
- **Activities** — list/detail with HR graph and time-in-zones.
- **Healthspan (phase 2)** — transparent composite "Pace of Aging" number.
- **AI Coach (phase 2)** — optional chat with a user-provided LLM API key (stored in
  EncryptedSharedPreferences; endpoint is user-configurable). Disabled unless you
  supply a key.
- **Demo mode** — 30 days of synthetic data so the UI works with no wearable at all.

## Data sources

| Source | Status | Notes |
|---|---|---|
| **Health Connect** | ✅ primary | All record types Pulse uses, Changes-API incremental sync, backfill to the maximum available history, background reads. |
| **File import** | ✅ always available | Garmin .FIT files, Garmin GDPR export, Zepp CSV, Apple-style CSV. |
| **Garmin direct** | ⚠️ experimental | Official Garmin Health API requires developer approval; the personal-use backend wraps the *unofficial* `garminconnect` library. Unofficial = can break or be blocked at any time. |
| **Zepp/Amazfit direct** | ⚠️ experimental | Unofficial Zepp/Huami cloud API via the personal-use backend. Same warnings. |

When more than one app writes the same metric, you choose the per-metric priority
(`Connectivity → source priority`). Missing inputs (HRV, SpO₂, skin temp are the usual
suspects) make the recovery weights **renormalize gracefully** instead of failing.

## The physiological day

A "day" runs **wake-up to next wake-up**, not midnight to midnight, so late nights and
sleep-ins are attributed correctly. Late-arriving records from delayed devices trigger
recomputation of the affected day on the next sync.

## Tech stack

Kotlin · Jetpack Compose (Material 3, dark-first) · MVVM + Clean Architecture ·
Hilt · Coroutines/Flow · Room + SQLCipher (encrypted at rest) · WorkManager
(15-minute periodic + on-open sync) · `androidx.health.connect:connect-client`.

## Building

```bash
./gradlew :app:assembleDebug          # debug build
./gradlew :app:lintDebug              # lint
./gradlew :app:testDebugUnitTest      # algorithm unit tests
```

Requirements: JDK 17, Android SDK with platform 35. `minSdk 28`, `targetSdk 35`.

There is no checked-in wrapper JAR; run `gradle wrapper --gradle-version 8.11.1` once, or
open in Android Studio (which provisions Gradle 8.11.1 automatically).

## Project layout

```
app/src/main/kotlin/com/pulse/app/
├── core/
│   ├── algorithm/   # all scoring math; every tunable in AlgorithmsConfig.kt
│   ├── model/       # domain models + behavior catalog
│   └── util/        # math/stats (erf, t-test, EWMAs)
├── data/
│   ├── db/          # Room entities, DAOs, encrypted DB, key provider
│   ├── source/      # DataSource interface + HC/mock/Garmin/Zepp/file impls
│   ├── repo/        # ingest, merge, score pipeline, journal, profile
│   └── prefs/       # DataStore settings (keys via EncryptedSharedPreferences)
├── work/            # SyncCoordinator + 15-min WorkManager worker
├── notify/          # morning recovery notification
├── di/              # Hilt modules
└── ui/              # Compose screens, nav, charts
backend/             # optional personal-use FastAPI gateway (experimental)
docs/                # privacy policy, algorithm docs
```

## Privacy

- Everything is computed and stored **on-device** (SQLCipher-encrypted Room + KeyStore)
  and no data leaves the phone unless you (a) point Pulse at a personal-use backend or
  (b) enable the AI coach — both are opt-in and off by default.
  Full policy: [docs/PRIVACY.md](docs/PRIVACY.md).
- In-app privacy-policy activity and Health Connect permission-rationale screens are
  included, as required by Health Connect.

## Milestone map

1. Skeleton, DB, mock data, dashboard UI ✅
2. Health Connect integration and sync ✅
3. Recovery / Strain / Sleep engines + unit tests ✅
4. Detail screens and trends ✅
5. Journal and notifications ✅
6. File import (FIT/CSV) ✅
7. Experimental Garmin/Zepp backend ✅ (see `backend/`)
8. Stress, Healthspan, AI coach ✅

## Limitations

- The unofficial Garmin/Zepp backends are personal-use prototypes; Google's Health
  Connect surface depends on each vendor app writing HRV/SpO₂/skin-temp records, which
  many devices do not.
- Health Connect's background-read and full-history permissions need explicit user
  granting on the device; without them, history is limited to ~30 days.
- The AI coach posts your summary text to the endpoint you configure; the endpoint you
  name must speak the OpenAI-style `/chat/completions` JSON shape.
