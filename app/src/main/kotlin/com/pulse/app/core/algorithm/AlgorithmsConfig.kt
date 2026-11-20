package com.pulse.app.core.algorithm

object AlgorithmsConfig {
    const val BASELINE_DECAY = 0.08
    const val BASELINE_MIN_DAYS = 4
    const val BASELINE_WINDOW_DAYS = 30

    const val RECOVERY_K = 0.8

    const val W_HRV = 0.45
    const val W_RHR = 0.25
    const val W_RESP = 0.10
    const val W_SPO2 = 0.05
    const val W_SKIN_TEMP = 0.05
    const val W_SLEEP_PERF = 0.10

    const val TRIMP_C = 15.0
    const val TRIMP_MAX = 600.0
    const val BANISTER_MALE_A = 0.64
    const val BANISTER_MALE_B = 1.92
    const val BANISTER_FEMALE_A = 0.86
    const val BANISTER_FEMALE_B = 1.67

    const val SLEEP_NEED_BASE_MIN = 480.0
    const val SLEEP_STRAIN_ADD_MAX_MIN = 45.0
    const val SLEEP_DEBT_WINDOW = 5
    const val SLEEP_DEBT_FACTOR = 0.5
    const val SLEEP_DEBT_CAP_MIN = 120.0
    const val NAP_MAX_MIN = 180
    const val CONSISTENCY_SCALE = 1.5

    const val STRESS_WINDOW_MIN = 5L
    const val STRESS_Z_CALM = 0.5
    const val STRESS_Z_MILD = 1.5
    const val STRESS_Z_HIGH = 3.0

    const val TARGET_STRAIN_RED = 8.0
    const val TARGET_STRAIN_YELLOW = 12.0
    const val TARGET_STRAIN_GREEN = 16.0

    const val HEALTHSPAN_STEPS_TARGET = 8000.0
    const val HEALTHSPAN_SLEEP_TARGET_H = 7.5
    const val HEALTHSPAN_HR_ZONE_TARGET_MIN = 60.0
    const val HEALTHSPAN_STRENGTH_PER_WEEK = 2.0
    const val HEALTHSPAN_VO2_TARGET_MALE = 45.0
    const val HEALTHSPAN_VO2_TARGET_FEMALE = 38.0
}
