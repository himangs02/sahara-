package com.yourteam.sahara.api

import com.yourteam.sahara.api.model.GameResultDto
import com.yourteam.sahara.api.model.GameResultUpsertDto
import com.yourteam.sahara.api.model.LoginRequestDto
import com.yourteam.sahara.api.model.PatientCreateDto
import com.yourteam.sahara.api.model.PatientDto
import com.yourteam.sahara.api.model.PatientUpdateDto
import com.yourteam.sahara.api.model.RegisterRequestDto
import com.yourteam.sahara.api.model.ReminderDto
import com.yourteam.sahara.api.model.ReminderUpsertDto
import com.yourteam.sahara.api.model.TokenResponseDto
import com.yourteam.sahara.api.model.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** The Stage 1/2/3B FastAPI endpoints Android is allowed to call directly. Android never talks to
 * PostgreSQL -- every one of these round-trips through this backend only. */
interface SaharaApiService {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): TokenResponseDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenResponseDto

    @GET("auth/me")
    suspend fun me(): UserDto

    @GET("patients")
    suspend fun getPatients(): List<PatientDto>

    @GET("patients/{patientId}")
    suspend fun getPatient(@Path("patientId") patientId: String): PatientDto

    @POST("patients")
    suspend fun createPatient(@Body body: PatientCreateDto): PatientDto

    @PATCH("patients/{patientId}")
    suspend fun updatePatient(@Path("patientId") patientId: String, @Body body: PatientUpdateDto): PatientDto

    @POST("patients/{patientId}/game-results")
    suspend fun uploadGameResult(@Path("patientId") patientId: String, @Body body: GameResultUpsertDto): GameResultDto

    @GET("patients/{patientId}/game-results")
    suspend fun getGameResults(@Path("patientId") patientId: String): List<GameResultDto>

    @POST("patients/{patientId}/reminders")
    suspend fun uploadReminder(@Path("patientId") patientId: String, @Body body: ReminderUpsertDto): ReminderDto

    @GET("patients/{patientId}/reminders")
    suspend fun getReminders(@Path("patientId") patientId: String): List<ReminderDto>

    @DELETE("patients/{patientId}/reminders/{reminderId}")
    suspend fun deleteReminder(@Path("patientId") patientId: String, @Path("reminderId") reminderId: String)
}
