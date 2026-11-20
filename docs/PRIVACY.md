# Privacy policy

*Last updated: 2026-11-18. Pulse is an open personal-use project, not a medical device.*

## Not a medical device

Pulse provides informational wellness scores (Recovery, Strain, Sleep, Stress) computed
from wearable-derived statistics. It is **not** a medical device and does not provide
medical diagnosis, treatment, or advice. Never make medical decisions based on Pulse;
consult a healthcare professional.

## Data we collect

Pulse collects **nothing**. There is no developer-operated server, no analytics, no
advertising SDK, no crash reporter, no telemetry.

## Where your data lives

- Physiological data read through Health Connect, file imports, or the optional
  experimental backends is stored **only on your device**, in a SQLCipher-encrypted
  Room database. The database key is a random 32-byte value kept in
  EncryptedSharedPreferences backed by the Android KeyStore.
- Backend session tokens (if you use the experimental Garmin/Zepp gateway) and your AI
  coach API key (if you set one) are stored in EncryptedSharedPreferences and never
  written to logs.

## When data leaves the device

By default, never. Two opt-in features send data off-device:

1. **Experimental Garmin/Zepp backend** — if you configure a personal gateway URL,
   your device calls *your* backend with your credentials. The backend contacts Garmin
   or Zepp on your behalf. Neither this project nor any third party receives your data;
   run the gateway only on infrastructure you trust.
2. **AI coach** — if you enter an LLM API key, a short summary of your daily metrics
   (scores and baseline means) is sent to the endpoint you configure, along with your
   question. No raw samples are sent. You can change or delete the key at any time in
   Settings.

## Health Connect

Pulse requests only read permissions for the record types listed in the manifest
(heart rate, HRV, resting HR, sleep, SpO₂, respiratory rate, skin temperature, steps,
exercise, VO₂ max, active calories) plus background-read and history permissions so
that syncs can happen when the app is closed and sleep history older than 30 days can
be backfilled. Pulses never shares Health Connect data with other apps or services.

## Deleting your data

- **Settings → Delete all data** wipes the local database and all stored tokens/keys.
- Revoking Health Connect permissions in the system settings stops all future reads.
