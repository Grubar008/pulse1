package com.pulse.app.data.source.zepp

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pulse.app.core.model.*
import com.pulse.app.data.source.DataSource
import com.pulse.app.data.source.gateway.AuthResponse
import com.pulse.app.data.source.gateway.SecureTokenStore
import com.pulse.app.data.source.gateway.ZeppSummaryDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.time.Instant
import java.time.ZoneId

interface ZeppBackendApi {
    @POST("zepp/login")
    suspend fun login(@Body body: ZeppLoginRequest): AuthResponse

    @GET("zepp/daily")
    suspend fun daily(@Header("Authorization") token: String, @retrofit2.http.Query("from") from: String, @retrofit2.http.Query("to") to: String): List<ZeppSummaryDto>
}

@kotlinx.serialization.Serializable
data class ZeppLoginRequest(val email: String, val password: String)

@kotlinx.serialization.Serializable
data class ZeppAuthResponse(val token: String)

/**
 * EXPERIMENTAL. Unofficial Zepp/Huami cloud access via a personal-use backend.
 * Credentials never leave the device except to your own backend over TLS.
 */
class ZeppDirectSource(
    baseUrl: String,
    private val tokens: SecureTokenStore
) : DataSource {

    override val id: SourceId = SourceId.ZEPP

    override val availableMetrics: Set<MetricType> = setOf(
        MetricType.HR, MetricType.HRV, MetricType.RHR, MetricType.SPO2,
        MetricType.RESP_RATE, MetricType.STEPS, MetricType.SLEEP
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val api: ZeppBackendApi by lazy {
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ZeppBackendApi::class.java)
    }

    private fun auth(): String? = tokens.load("zepp", "session")?.let { "Bearer $it" }

    suspend fun login(email: String, password: String) {
        val resp = api.login(ZeppLoginRequest(email, password))
        tokens.save("zepp", "session", resp.token)
    }

    override suspend fun status(): SourceStatus =
        if (auth() != null) SourceStatus.Connected else SourceStatus.Disconnected

    override suspend fun readHeartRate(range: TimeRange): List<HrSample> = emptyList()

    override suspend fun readHrv(range: TimeRange): List<HrvSample> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto ->
            dto.hrvRmssd?.let { rmssd ->
                HrvSample(dayInstant(dto.date, 6), rmssd, SourceId.ZEPP)
            }
        }
    }

    override suspend fun readRestingHr(range: TimeRange): List<HrSample> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto ->
            dto.restingHeartRate?.let { HrSample(dayInstant(dto.date, 12), it, SourceId.ZEPP) }
        }
    }

    override suspend fun readSleepSessions(range: TimeRange): List<SleepSession> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto ->
            val start = dto.sleepStartTs ?: return@mapNotNull null
            val end = dto.sleepEndTs ?: return@mapNotNull null
            val stages = listOf(
                SleepStage(SleepStageType.DEEP, Instant.ofEpochSecond(start), Instant.ofEpochSecond(start + dto.deepMin * 60)),
                SleepStage(SleepStageType.LIGHT, Instant.ofEpochSecond(start + dto.deepMin * 60), Instant.ofEpochSecond(start + (dto.deepMin + dto.lightMin) * 60)),
                SleepStage(SleepStageType.REM, Instant.ofEpochSecond(start + (dto.deepMin + dto.lightMin) * 60), Instant.ofEpochSecond(end - dto.awakeMin * 60)),
                SleepStage(SleepStageType.AWAKE, Instant.ofEpochSecond(end - dto.awakeMin * 60), Instant.ofEpochSecond(end))
            ).filter { it.end > it.start }
            SleepSession(Instant.ofEpochSecond(start), Instant.ofEpochSecond(end), stages, SourceId.ZEPP)
        }
    }

    override suspend fun readSteps(range: TimeRange): List<StepsBucket> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto ->
            dto.steps?.let { StepsBucket(dayInstant(dto.date, 0), dayInstant(dto.date, 24), it.toLong(), SourceId.ZEPP) }
        }
    }

    override suspend fun readExercise(range: TimeRange): List<Workout> = emptyList()

    override suspend fun readSpO2(range: TimeRange): List<Spo2Sample> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto ->
            dto.spo2Avg?.let { Spo2Sample(dayInstant(dto.date, 3), it, SourceId.ZEPP) }
        }
    }

    override suspend fun readRespiratoryRate(range: TimeRange): List<RespSample> {
        val (from, to) = dates(range)
        return api.daily(requireAuth(), from, to).mapNotNull { dto ->
            dto.respRate?.let { RespSample(dayInstant(dto.date, 4), it, SourceId.ZEPP) }
        }
    }

    override suspend fun readSkinTemp(range: TimeRange): List<SkinTempSample> = emptyList()
    override suspend fun readVo2Max(range: TimeRange): List<Pair<Instant, Double>> = emptyList()

    private fun requireAuth(): String = auth() ?: throw IllegalStateException("Zepp not connected")

    private fun dates(range: TimeRange): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        return java.time.LocalDate.ofInstant(range.start, zone).toString() to
            java.time.LocalDate.ofInstant(range.end, zone).toString()
    }

    private fun dayInstant(date: String, hour: Int): Instant =
        java.time.LocalDate.parse(date).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant()
}
