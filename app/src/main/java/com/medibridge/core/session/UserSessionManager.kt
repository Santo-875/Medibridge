package com.medibridge.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medibridge_user_session")

class UserSessionManager private constructor(private val context: Context) {

    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val ACTIVE_PROFILE_NAME = stringPreferencesKey("active_profile_name")
        val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
    }

    fun getCachedUserId(): String? = com.medibridge.core.auth.AuthTokenManager.getInstance(context).getUserId()

    val userIdFlow: Flow<String> = context.dataStore.data.map {
        com.medibridge.core.auth.AuthTokenManager.getInstance(context).getUserId() ?: ""
    }

    val activeProfileIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
    }

    val activeProfileNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.ACTIVE_PROFILE_NAME] ?: (com.medibridge.core.auth.AuthTokenManager.getInstance(context).getUserName() ?: "Alex")
    }

    suspend fun getOrCreateUserId(): String {
        return com.medibridge.core.auth.AuthTokenManager.getInstance(context).getUserId() ?: ""
    }

    suspend fun getLastSyncTime(): String? {
        return context.dataStore.data.first()[PreferencesKeys.LAST_SYNC_TIME]
    }

    suspend fun setLastSyncTime(timeIso: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_SYNC_TIME] = timeIso
        }
    }

    suspend fun getActiveProfileId(): String {
        return context.dataStore.data.first()[PreferencesKeys.ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
    }

    suspend fun setActiveProfile(profileId: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACTIVE_PROFILE_ID] = profileId
            prefs[PreferencesKeys.ACTIVE_PROFILE_NAME] = name
        }
    }

    suspend fun createMeProfile(name: String) {
        val userId = getOrCreateUserId()
        val db = AppDatabase.getInstance(context)
        db.profileDao().insertProfile(
            ProfileEntity(
                id = DEFAULT_PROFILE_ID,
                ownerUserId = userId,
                name = name,
                avatar = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                synced = false
            )
        )
        setActiveProfile(DEFAULT_PROFILE_ID, name)
    }

    suspend fun ensureInitialized() {
        val tokenMgr = com.medibridge.core.auth.AuthTokenManager.getInstance(context)
        val userId = tokenMgr.getUserId() ?: return
        val name = tokenMgr.getUserName() ?: "Alex"
        val db = AppDatabase.getInstance(context)
        val defaultProfile = db.profileDao().getProfileById(DEFAULT_PROFILE_ID)
        if (defaultProfile == null) {
            db.profileDao().insertProfile(
                ProfileEntity(
                    id = DEFAULT_PROFILE_ID,
                    ownerUserId = userId,
                    name = name,
                    avatar = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    synced = false
                )
            )
        }
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "default_me"

        @Volatile
        private var INSTANCE: UserSessionManager? = null

        fun getInstance(context: Context): UserSessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = UserSessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
