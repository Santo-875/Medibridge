package com.medibridge.core.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.medibridge.BuildConfig
import com.medibridge.core.db.MedicationDao
import com.medibridge.core.model.AdherenceRecord
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.ScheduleSlot
import com.medibridge.core.model.toEntity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

// ── DTOs for Prescription Scanning ──────────────────────────────────────────

data class PrescriptionUploadResponse(
    val message: String? = null,
    val filename: String? = null,
    @SerializedName("content_type") val contentType: String? = null,
    val size: Long? = null,
    @SerializedName("prescription_record") val prescriptionRecord: ApiPrescriptionRecord? = null
)

data class ApiPrescriptionRecord(
    val patient: ApiPatientInfo? = null,
    val prescription: ApiPrescriptionInfo? = null,
    val medications: List<ApiMedicationItem> = emptyList()
)

data class ApiPatientInfo(
    val name: String? = null,
    val age: Int? = null,
    val relation: String? = null,
    val mobile: String? = null
)

data class ApiPrescriptionInfo(
    val date: String? = null,
    val time: String? = null,
    @SerializedName("prescribed_by") val prescribedBy: String? = null,
    val remarks: String? = null,
    @SerializedName("delivery_type") val deliveryType: String? = null,
    @SerializedName("token_no") val tokenNo: String? = null
)

data class ApiMedicationItem(
    val medicine: String,
    @SerializedName("normalized_name") val normalizedName: String? = null,
    val dosage: String? = null,
    val instruction: String? = null,
    @SerializedName("duration_days") val durationDays: Int? = null,
    val pqt: String? = null,
    val iqt: String? = null,
    val balance: String? = null,
    val validation: ApiValidationInfo? = null
)

data class ApiValidationInfo(
    val rxcui: String? = null,
    val status: String? = null, // "HIGH", "NEEDS_VERIFICATION", "UNVERIFIED"
    val reason: String? = null
)

// ── DTOs for Speech-to-Text ──────────────────────────────────────────────────

data class SpeechUploadResponse(
    val id: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val transcript: String? = null,
    val summary: ApiSpeechSummary? = null
)

data class ApiSpeechSummary(
    @SerializedName("chief_complaints") val chiefComplaints: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val diagnosis: String? = null,
    val medications: List<ApiSpeechMedication> = emptyList(),
    @SerializedName("advice_and_precautions") val adviceAndPrecautions: List<String> = emptyList(),
    @SerializedName("follow_up") val followUp: String? = null,
    @SerializedName("doctor_notes_summary") val doctorNotesSummary: String? = null
)

data class ApiSpeechMedication(
    val name: String,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null
)

// ── Retrofit API Interface ───────────────────────────────────────────────────

interface BackendApiService {

    @Multipart
    @POST("api/prescriptions")
    suspend fun uploadPrescription(
        @Part file: MultipartBody.Part
    ): PrescriptionUploadResponse

    @Multipart
    @POST("api/billing/pdf")
    suspend fun uploadBillingPdf(
        @Part file: MultipartBody.Part
    ): PrescriptionUploadResponse

    @Multipart
    @POST("api/speech/upload")
    suspend fun uploadSpeech(
        @Part file: MultipartBody.Part
    ): SpeechUploadResponse
}

// ── Backend Repository & Mapping ─────────────────────────────────────────────

object BackendClient {

    private var currentBaseUrl: String = BuildConfig.BACKEND_BASE_URL

    private fun getOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private var cachedService: BackendApiService? = null
    private var cachedUrl: String = ""

    fun getService(baseUrl: String = currentBaseUrl): BackendApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (cachedService != null && cachedUrl == normalizedUrl) {
            return cachedService!!
        }
        cachedUrl = normalizedUrl
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(getOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()

        val service = retrofit.create(BackendApiService::class.java)
        cachedService = service
        return service
    }

    fun updateBaseUrl(newUrl: String) {
        currentBaseUrl = newUrl
        cachedService = null
    }

    fun getBaseUrl(): String = currentBaseUrl

    /**
     * Maps an ApiMedicationItem into a full authoritative MedicationObject.
     * Module A contract: confidence < 0.80f -> needsVerification = true.
     */
    fun mapPrescriptionItemToObject(
        item: ApiMedicationItem,
        doctorRemarks: String? = null,
        imageUrl: String? = null
    ): MedicationObject {
        val rawStatus = item.validation?.status?.uppercase() ?: "HIGH"
        val calculatedConfidence = when (rawStatus) {
            "HIGH" -> 0.95f
            "NEEDS_VERIFICATION" -> 0.70f
            else -> 0.40f
        }
        val needsVerification = calculatedConfidence < 0.80f

        val displayName = item.normalizedName ?: item.medicine
        val durationStr = item.durationDays?.let { "$it days" } ?: "30 days"
        val timingStr = item.instruction ?: "As directed"
        val doseStr = item.dosage ?: "1 unit"

        return MedicationObject(
            id = UUID.randomUUID().toString(),
            name = displayName,
            strength = extractStrength(item.medicine) ?: doseStr,
            dose = doseStr,
            frequency = inferFrequency(doseStr, timingStr),
            timing = timingStr,
            duration = durationStr,
            confidence = calculatedConfidence,
            needsVerification = needsVerification,
            verifiedByUser = false,
            crossVerified = false,
            conflicts = emptyList(),
            reviewRecommended = needsVerification,
            schedule = generateDefaultSchedule(doseStr),
            adherence = emptyList(),
            summary = "Prescribed: $displayName. $timingStr",
            sideEffects = emptyList(),
            visibleTo = listOf("patient", "caregiver", "doctor"),
            imageUrl = imageUrl,
            sourceType = "bill",
            consultationNotes = doctorRemarks ?: ""
        )
    }

    /**
     * Maps a speech-to-text consultation medication into a MedicationObject.
     */
    fun mapSpeechMedicationToObject(
        speechMed: ApiSpeechMedication,
        summary: ApiSpeechSummary
    ): MedicationObject {
        val notes = buildString {
            if (!summary.doctorNotesSummary.isNullOrBlank()) {
                append(summary.doctorNotesSummary)
            }
            if (!summary.diagnosis.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append("Diagnosis: ").append(summary.diagnosis)
            }
        }

        val frequencyStr = speechMed.frequency ?: "Once daily"
        val doseStr = speechMed.dosage ?: "1 tablet"
        val timingStr = speechMed.instructions ?: "As directed"

        return MedicationObject(
            id = UUID.randomUUID().toString(),
            name = speechMed.name,
            strength = extractStrength(speechMed.name) ?: doseStr,
            dose = doseStr,
            frequency = frequencyStr,
            timing = timingStr,
            duration = speechMed.duration ?: "30 days",
            confidence = 0.92f,
            needsVerification = false,
            verifiedByUser = false,
            crossVerified = false,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = generateDefaultSchedule(frequencyStr),
            adherence = emptyList(),
            summary = "Consultation voice order: ${speechMed.name} ($doseStr). $timingStr",
            sideEffects = emptyList(),
            visibleTo = listOf("patient", "caregiver", "doctor"),
            sourceType = "voice",
            consultationNotes = notes
        )
    }

    private fun extractStrength(text: String): String? {
        val regex = Regex("""\b\d+(\.\d+)?\s*(mg|mcg|g|ml|%)\b""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value
    }

    private fun inferFrequency(dosage: String, instruction: String): String {
        val combined = "$dosage $instruction".lowercase()
        return when {
            combined.contains("bd") || combined.contains("twice") || combined.contains("2 x") -> "Twice daily"
            combined.contains("tid") || combined.contains("three") || combined.contains("3 x") -> "Three times daily"
            combined.contains("hs") || combined.contains("bedtime") || combined.contains("night") -> "Once daily (Bedtime)"
            combined.contains("morning") || combined.contains("breakfast") -> "Once daily (Morning)"
            else -> "Daily"
        }
    }

    private fun generateDefaultSchedule(frequency: String): List<ScheduleSlot> {
        val freq = frequency.lowercase()
        return when {
            freq.contains("twice") || freq.contains("bd") -> listOf(
                ScheduleSlot(time = "08:00", slot = "Morning", withFood = true),
                ScheduleSlot(time = "20:00", slot = "Night", withFood = true)
            )
            freq.contains("three") || freq.contains("tid") -> listOf(
                ScheduleSlot(time = "08:00", slot = "Morning", withFood = true),
                ScheduleSlot(time = "13:00", slot = "Afternoon", withFood = true),
                ScheduleSlot(time = "20:00", slot = "Night", withFood = true)
            )
            freq.contains("night") || freq.contains("bed") -> listOf(
                ScheduleSlot(time = "21:00", slot = "Night", withFood = false)
            )
            else -> listOf(
                ScheduleSlot(time = "09:00", slot = "Morning", withFood = true)
            )
        }
    }
}
