package com.medibridge.core.network

import com.google.gson.annotations.SerializedName

data class ProfileDto(
    val id: String,
    @SerializedName("owner_user_id") val ownerUserId: String,
    val name: String,
    val avatar: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class MedicationDto(
    val id: String,
    @SerializedName("profile_id") val profileId: String,
    val name: String,
    val dosage: String? = null,
    val strength: String? = null,
    val timing: String? = null,
    @SerializedName("food_rule") val foodRule: String? = null,
    @SerializedName("duration_days") val durationDays: Int? = null,
    val source: String? = null,
    val rxcui: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class DoseTimeDto(
    val id: String,
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("profile_id") val profileId: String,
    val time: String,
    val slot: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class DoseLogDto(
    val id: String,
    @SerializedName("profile_id") val profileId: String,
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("dose_time_id") val doseTimeId: String? = null,
    @SerializedName("scheduled_time") val scheduledTime: String,
    val status: String,
    @SerializedName("taken_at") val takenAt: String? = null,
    @SerializedName("snooze_until") val snoozeUntil: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class CareLinkDto(
    val id: String,
    @SerializedName("profile_id") val profileId: String,
    @SerializedName("caretaker_user_id") val caretakerUserId: String? = null,
    val permission: String,
    val status: String,
    @SerializedName("invite_code") val inviteCode: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RecordingDto(
    val id: String,
    @SerializedName("profile_id") val profileId: String,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("transcript_tamil") val transcriptTamil: String? = null,
    @SerializedName("transcript_english") val transcriptEnglish: String? = null,
    @SerializedName("summary_json") val summaryJson: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class SafetyCheckDto(
    val id: String,
    @SerializedName("profile_id") val profileId: String,
    @SerializedName("medication_id") val medicationId: String? = null,
    @SerializedName("conflict_type") val conflictType: String,
    val severity: String,
    val details: String? = null,
    @SerializedName("action_required") val actionRequired: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class NotificationDto(
    val id: String,
    @SerializedName("profile_id") val profileId: String,
    val type: String,
    val message: String,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class SyncPushRequest(
    val profiles: List<ProfileDto> = emptyList(),
    val medications: List<MedicationDto> = emptyList(),
    @SerializedName("dose_times") val doseTimes: List<DoseTimeDto> = emptyList(),
    @SerializedName("dose_logs") val doseLogs: List<DoseLogDto> = emptyList(),
    val recordings: List<RecordingDto> = emptyList(),
    @SerializedName("safety_checks") val safetyChecks: List<SafetyCheckDto> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    @SerializedName("care_links") val careLinks: List<CareLinkDto> = emptyList()
)

data class SyncPushResponse(
    val status: String? = null,
    @SerializedName("upserted_count") val upsertedCount: Int? = null
)

data class SyncPullResponse(
    @SerializedName("server_time") val serverTime: String? = null,
    val profiles: List<ProfileDto> = emptyList(),
    val medications: List<MedicationDto> = emptyList(),
    @SerializedName("dose_times") val doseTimes: List<DoseTimeDto> = emptyList(),
    @SerializedName("dose_logs") val doseLogs: List<DoseLogDto> = emptyList(),
    val recordings: List<RecordingDto> = emptyList(),
    @SerializedName("safety_checks") val safetyChecks: List<SafetyCheckDto> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    @SerializedName("care_links") val careLinks: List<CareLinkDto> = emptyList()
)

data class ApiProfileResponse(
    val id: String,
    @SerializedName("owner_user_id") val ownerUserId: String,
    val name: String,
    val avatar: String? = null,
    @SerializedName("is_owner") val isOwner: Boolean = true,
    val permission: String = "owner"
)

data class CareInviteRequest(
    @SerializedName("profile_id") val profileId: String,
    val permission: String = "view"
)

data class CareInviteResponse(
    @SerializedName("care_link_id") val careLinkId: String,
    @SerializedName("profile_id") val profileId: String,
    @SerializedName("invite_code") val inviteCode: String,
    val permission: String,
    val status: String
)

data class CareAcceptRequest(
    @SerializedName("invite_code") val inviteCode: String
)

data class CareAcceptResponse(
    @SerializedName("care_link_id") val careLinkId: String,
    @SerializedName("profile_id") val profileId: String,
    @SerializedName("profile_name") val profileName: String,
    val permission: String,
    val status: String
)

data class CareRevokeRequest(
    @SerializedName("care_link_id") val careLinkId: String
)

data class DeviceRegisterRequest(
    @SerializedName("fcm_token") val fcmToken: String,
    val platform: String = "android"
)
