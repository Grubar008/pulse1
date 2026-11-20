package com.pulse.app.data.source.garmin

import com.pulse.app.core.model.*
import com.pulse.app.data.source.DataSource
import com.pulse.app.data.source.gateway.GarminDaily
import com.pulse.app.data.source.gateway.GarminLoginRequest
import com.pulse.app.data.source.gateway.GarminSleepDto
import com.pulse.app.data.source.gateway.SecureTokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.time.Instant
import java.time.ZoneId

interface GarminBackendApi {
    @POST("login")
    suspend fun login(@Body body: GarminLoginRequest): AuthResponse

    @GET("garmin/daily")
    suspend fun daily(@Header("Authorization") token: String, @retrofit2.http.Query("from") from: String, @retrofit2.http.Query("to") to: String): List<GarminDaily>

    @GET("garmin/sleep")
    suspend fun sleep(@Header("Authorization") token: String, @retrofit2.http.Query("from") from: String, @retrofit2.http.Query("to") to: String): List<GarminSleepDto>
}

/**
 * EXPERIMENTAL. Requires a personal-use backend wrapping python-garminconnect.
 * This is unofficial: Garmin credentials are verified by the backend and only an
 * opaque session token is stored on-device (encrypted). Never logged.
 * The alternative is the official Garmin Health API (OAuth2, push/ping) which
 * requires Garmin developer approval: https://developer.garmin.com/
 */
class GarminDirectSource(
    baseUrl: String,
    private val tokens: SecureTokenStore
) : DataSource {

    override val id: SourceId = SourceId.GARMIN

    override val availableMetrics: Set<MetricType> = setOf(
        MetricType.HR, MetricType.HRV, MetricType.RHR, MetricType.STEPS, MetricType.SLEEP
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val api: GarminBackendApi by lazy {
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GarminBackendApi::class.java)
    }

    private fun auth(): String? = tokens.load("garmin", "session")?.let { "Bearer $it" }

    suspend fun login(email: String, password: String) {
        val resp = api.login(GarminLoginRequest(email, password))
        tokens.save("garmin", "session", resp.token)
    }

    override suspend fun status(): SourceStatus {
        return if (auth() != null) SourceStatus.Connected else SourceStatus.Disconnected
    }

    private fun requireAuth(): String = auth() ?: throw IllegalStateException("Garmin not connected")

    private fun dates(range: TimeRange): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        return isoDay(range.start, zone) to isoDay(range.end, zone)
    }

    private fun isoDay(instant: Instant, zone: ZoneId): String =
        java.time.LocalDate.ofInstant(instant, zone).toString()

    override suspend fun readHeartRate(range: TimeRange): List<HrSample> = emptyList()

    override suspend fun readHrv(range: TimeRange): List<HrvSample> {
        val (from, to) = dates(range)
        return api.sleep(requireAuth(), from, to).mapNotNull { dto ->
            (dto.rmssd ?: dto.restingHeartRate)?.let { rms ->
                HrvSample(Instant.ofEpochSecond(dto.startTs), rms, SourceId.GARMIN)
            }
        }
    }

    override suspend fun readRestingHr(range: TimeRange): List<HrSample> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto: GarminDaily ->
            dto.restingHeartRate?.let { rhr ->
                HrSample(java.time.LocalDate.parse(dto.date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(), rhr, SourceId.GARMIN)
            }
        }
    }

    override suspend fun readSleepSessions(range: TimeRange): List<SleepSession> {
        val (from, to) = dates(range)
        return api.sleep(requireAuth(), from, to).map { dto ->
            val stages = listOf(
                SleepStage(SleepStageType.DEEP, Instant.ofEpochSecond(dto.startTs), Instant.ofEpochSecond(dto.startTs + dto.deepMin * 60)),
                SleepStage(SleepStageType.LIGHT, Instant.ofEpochSecond(dto.startTs + dto.deepMin * 60), Instant.ofEpochSecond(dto.startTs + (dto.deepMin + dto.lightMin) * 60)),
                SleepStage(SleepStageType.REM, Instant.ofEpochSecond(dto.startTs + (dto.deepMin + dto.lightMin) * 60), Instant.ofEpochSecond(dto.endTs - dto.awakeMin * 60)),
                SleepStage(SleepStageType.AWAKE, Instant.ofEpochSecond(dto.endTs - dto.awakeMin * 60), Instant.ofEpochSecond(dto.endTs))
            ).filter { it.end > it.start }
            SleepSession(Instant.ofEpochSecond(dto.startTs), Instant.ofEpochSecond(dto.endTs), stages, SourceId.GARMIN)
        }
    }

    override suspend fun readSteps(range: TimeRange): List<StepsBucket> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto ->
            dto.steps?.let { steps ->
                val t = java.time.LocalDate.parse(dto.date).atStartOfDay(ZoneId.systemDefault()).toInstant()
                StepsBucket(t, t.plusSeconds(86400), steps.toLong(), SourceId.GARMIN)
            }
        }
    }

    override suspend fun readExercise(range: TimeRange): List<Workout> = emptyList()
    override suspend fun readSpO2(range: TimeRange): List<Spo2Sample> = emptyList()
    override suspend fun readRespiratoryRate(range: TimeRange): List<RespSample> = emptyList()
    override suspend fun readSkinTemp(range: TimeRange): List<SkinTempSample> = emptyList()
    override suspend fun readVo2Max(range: TimeRange): List<Pair<Instant, Double>> = emptyList()
}
