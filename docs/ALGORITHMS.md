# Algorithms

Every tunable constant lives in
`app/src/main/kotlin/com/pulse/app/core/algorithm/AlgorithmsConfig.kt`. All the
engines below are pure functions of synthetic inputs and are covered by unit tests in
`app/src/test/kotlin/com/pulse/app/algorithm/`.

## Physiological day

`CycleSplitter` groups samples into cycles from **wake-up to the next wake-up**. Naps
(sleep sessions < 3 h outside the main sleep) are excluded from the main sleep; the
main sleep session in a cycle is the one ≥ 3 h.

## Baselines

- HRV is transformed with **ln(RMSSD)** before statistics are computed.
- Each metric (HRV, RHR, respiratory rate, SpO₂, skin temp) keeps a **30-day
  exponentially weighted mean and SD** (`EwStats`, decay `0.08`).
- SD floors prevent division blow-ups for very consistent users.
- The first **4 nights** are a calibration period: the dashboard shows
  "Calibrating" instead of scores.

## Recovery (0–100)

Inputs from the main sleep: HRV (ln RMSSD), RHR (lowest 5-min average during sleep),
respiratory rate, SpO₂, skin-temp deviation, sleep performance.

```
z_i = (measured_i − baseline_mean_i) / baseline_sd_i
composite = 0.45·zHRV − 0.25·zRHR − 0.10·zResp
          + 0.05·zSpO2 − 0.05·|zSkinTemp| + 0.10·zSleepPerf
w_dropped : weights are renormalized over the inputs that are present
recovery  = clip(round(100 · Φ(0.8 · composite)), 1, 99)
```

Zones: red 0–33, yellow 34–66, green 67–100. The detail screen shows each input's
contribution vs your baseline.

## Strain (0–21)

```
HRR(t)      = (HR(t) − RHR) / (HRmax − RHR),  HRmax = max(measured, 208 − 0.7·age)
TRIMP(t)    = minutes(t) · HRR(t) · (0.64·e^(1.92·HRR)  [male]
                                     | 0.86·e^(1.67·HRR)  [female])
TRIMP_daily = Σ over the physiological day (60-s buckets, workouts overlaid)
strain      = 21 · ln(1 + TRIMP_daily / c) / ln(1 + TRIMP_max / c)
              c = 15, TRIMP_max = 600
```

The HRmax estimate auto-upwards if you exceed it during a workout. **Muscular Load**
is estimated separately for strength sessions from type/duration and user-entered
sets/reps/weight.

## Sleep

- **Sleep need** = base (default 8 h; after 30 nights: personal rolling average)
  + strain addition (up to +45 min on high-strain days)
  + 50 % of the trailing 5 nights' debt, capped at 120 min − naps.
- **Sleep performance** = `min(100, asleep / need · 100)`.
- **Consistency** = `100 −` scaled SD of bed/wake times over the last 5 nights.
- **Efficiency** = `asleep / in-bed`.
- **Planner** inverts the performance formula for target performances of 70/85/100 %.

## Stress (0–3)

Non-sleep, non-workout time is scanned in 5-minute windows. Each window is compared to
your personal resting HR baseline; if RMSSD is available, a depressed HRV-z (< −1)
escalates the band. Band boundaries sit at z = 0.5 / 1.5 / 3.0 with a floor for
resting windows. The day view shows the stress curve plus time-per-band percentages.

## Journal impact

For each behavior: split days with and without the behavior, take next-day recovery in
both groups. Once each group has **≥ 5 samples**, run **Welch's t-test** and report the
mean difference, Cohen's d, and a confidence label:

| Label | Criteria |
|---|---|
| Strong | p < 0.01 and \|d\| ≥ 0.5 |
| Likely | p < 0.05 |
| Emerging | p < 0.15 |
| Inconclusive | otherwise |

## Healthspan (phase 2, informational)

A transparent documented composite of sleep hours, sleep consistency, HRV, RHR, VO₂
max, steps, time in HR zones 1–5, and strength-training frequency, normalized to a
"Pace of Aging" number. Explicitly not medical.
