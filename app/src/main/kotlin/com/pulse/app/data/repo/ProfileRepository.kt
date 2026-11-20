package com.pulse.app.data.repo

import com.pulse.app.core.model.Sex
import com.pulse.app.core.model.UserProfile
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.db.entity.UserProfileEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(private val db: PulseDatabase) {

    suspend fun get(): UserProfile {
        val e = db.profileDao().get() ?: return UserProfile()
        return UserProfile(
            name = e.name,
            age = e.age,
            sex = Sex.valueOf(e.sex.uppercase()),
            weightKg = e.weightKg,
            hrMax = e.hrMax,
            sleepNeedBaseMin = e.sleepNeedBaseMin,
            useImperial = e.useImperial,
            onboarded = e.onboarded
        )
    }

    suspend fun save(profile: UserProfile) {
        db.profileDao().upsert(
            UserProfileEntity(
                name = profile.name,
                age = profile.age,
                sex = profile.sex.name.uppercase(),
                weightKg = profile.weightKg,
                hrMax = profile.hrMax,
                sleepNeedBaseMin = profile.sleepNeedBaseMin,
                useImperial = profile.useImperial,
                onboarded = profile.onboarded
            )
        )
    }
}
