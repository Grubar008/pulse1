package com.pulse.app.core.model

object BehaviorCatalog {
    val defaults: List<BehaviorDef> = listOf(
        BehaviorDef("alcohol", "Alcohol", ValueType.SERVINGS),
        BehaviorDef("caffeine", "Caffeine", ValueType.SERVINGS),
        BehaviorDef("late_meal", "Late meal", ValueType.BOOLEAN),
        BehaviorDef("screen_before_bed", "Screen before bed", ValueType.BOOLEAN),
        BehaviorDef("magnesium", "Magnesium", ValueType.BOOLEAN),
        BehaviorDef("melatonin", "Melatonin", ValueType.BOOLEAN),
        BehaviorDef("vitamin_d", "Vitamin D", ValueType.BOOLEAN),
        BehaviorDef("creatine", "Creatine", ValueType.BOOLEAN),
        BehaviorDef("iron", "Iron supplement", ValueType.BOOLEAN),
        BehaviorDef("omega3", "Omega-3", ValueType.BOOLEAN),
        BehaviorDef("workout_hard", "Hard workout", ValueType.BOOLEAN),
        BehaviorDef("workout_strength", "Strength training", ValueType.BOOLEAN),
        BehaviorDef("sauna", "Sauna", ValueType.BOOLEAN),
        BehaviorDef("cold_shower", "Cold plunge", ValueType.BOOLEAN),
        BehaviorDef("meditation", "Meditation", ValueType.MINUTES),
        BehaviorDef("nap", "Nap", ValueType.MINUTES),
        BehaviorDef("travel", "Travel", ValueType.BOOLEAN),
        BehaviorDef("stressful_day", "Stressful day", ValueType.SCALE),
        BehaviorDef("weather_hot", "Hot weather", ValueType.BOOLEAN),
        BehaviorDef("air_quality", "Poor air quality", ValueType.BOOLEAN),
        BehaviorDef("sunlight_morning", "Morning sunlight", ValueType.BOOLEAN),
        BehaviorDef("bedtime_early", "Early bedtime", ValueType.BOOLEAN),
        BehaviorDef("bedtime_late", "Late bedtime", ValueType.BOOLEAN),
        BehaviorDef("hydration", "Good hydration", ValueType.BOOLEAN),
        BehaviorDef("spicy_food", "Spicy food", ValueType.BOOLEAN),
        BehaviorDef("sugar", "Sugar", ValueType.BOOLEAN)
    )

    fun all(custom: List<BehaviorDef>): List<BehaviorDef> = defaults + custom
}
