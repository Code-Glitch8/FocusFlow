package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AiProviderProfile
import com.example.model.AiProviderType
import com.example.model.AppSettings
import com.example.model.ShortsMode
import com.example.model.ThemeMode
import com.example.model.WidgetPresetTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.focusFlowDataStore by preferencesDataStore(name = "focusflow_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BLOCK_YOUTUBE_APP = booleanPreferencesKey("block_youtube_app")
        val BLOCK_YOUTUBE_BREAKS = booleanPreferencesKey("block_youtube_breaks")
        val BLOCK_YOUTUBE_SHORTS = stringPreferencesKey("block_youtube_shorts")
        val SKIP_HOME_FEED = booleanPreferencesKey("skip_home_feed")
        val BLOCK_ALL_EXCEPT_ALLOWED = booleanPreferencesKey("block_all_except_allowed")
        val ASSISTANT_BUBBLE = booleanPreferencesKey("assistant_bubble_enabled")
        val STRICT_DEFAULT = booleanPreferencesKey("strict_default")
        val VOICE_OFFLINE_ONLY = booleanPreferencesKey("voice_offline_only")
        val AUTO_SUMMARY = booleanPreferencesKey("auto_summary_enabled")
        val AUTO_LAUNCH_SNAPGPT = booleanPreferencesKey("auto_launch_snapgpt")
        val SNAPGPT_PACKAGE = stringPreferencesKey("snapgpt_package")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        val WIFI_SYNC_CODE = stringPreferencesKey("wifi_sync_code")
        val WIFI_SYNC_LAST_AT = longPreferencesKey("wifi_sync_last_at")
        val WIFI_SYNC_LAST_PEER = stringPreferencesKey("wifi_sync_last_peer")
        val CHAPTER_CONFIDENCE_JSON = stringPreferencesKey("chapter_confidence_json")
        val WIDGET_PRESET_TASKS_JSON = stringPreferencesKey("widget_preset_tasks_json")
        val ANDROID_WIDGET_BINDINGS_JSON = stringPreferencesKey("android_widget_bindings_json")
        val SEED_VERSION = intPreferencesKey("seed_version")
        val AI_PROVIDERS_JSON = stringPreferencesKey("ai_providers_json")
    }

    val settingsFlow: Flow<AppSettings> = context.focusFlowDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            blockYoutubeApp = prefs[Keys.BLOCK_YOUTUBE_APP] ?: false,
            blockYoutubeAppDuringBreaks = prefs[Keys.BLOCK_YOUTUBE_BREAKS] ?: false,
            blockYoutubeShorts = prefs[Keys.BLOCK_YOUTUBE_SHORTS]?.let { runCatching { ShortsMode.valueOf(it) }.getOrNull() } ?: ShortsMode.DURING_SESSIONS,
            skipYoutubeHomeFeed = prefs[Keys.SKIP_HOME_FEED] ?: false,
            blockAllExceptAllowed = prefs[Keys.BLOCK_ALL_EXCEPT_ALLOWED] ?: false,
            assistantBubbleEnabled = prefs[Keys.ASSISTANT_BUBBLE] ?: true,
            strictDefault = prefs[Keys.STRICT_DEFAULT] ?: true,
            voiceOfflineOnly = prefs[Keys.VOICE_OFFLINE_ONLY] ?: false,
            autoSummaryEnabled = prefs[Keys.AUTO_SUMMARY] ?: true,
            autoLaunchSnapGpt = prefs[Keys.AUTO_LAUNCH_SNAPGPT] ?: false,
            snapGptPackage = prefs[Keys.SNAPGPT_PACKAGE] ?: "com.snapgpt.assistant",
            activeProviderId = prefs[Keys.ACTIVE_PROVIDER_ID]?.ifBlank { null },
            wifiSyncCode = prefs[Keys.WIFI_SYNC_CODE] ?: "FF-4821",
            wifiSyncLastSyncedAt = prefs[Keys.WIFI_SYNC_LAST_AT],
            wifiSyncLastPeer = prefs[Keys.WIFI_SYNC_LAST_PEER]?.ifBlank { null },
            chapterConfidence = parseConfidenceJson(prefs[Keys.CHAPTER_CONFIDENCE_JSON]),
            widgetPresetTasks = parseWidgetTasksJson(prefs[Keys.WIDGET_PRESET_TASKS_JSON]),
            androidWidgetBindings = parseWidgetBindingsJson(prefs[Keys.ANDROID_WIDGET_BINDINGS_JSON]),
            seedVersion = prefs[Keys.SEED_VERSION] ?: 0
        )
    }

    val providersFlow: Flow<List<AiProviderProfile>> = context.focusFlowDataStore.data.map { prefs ->
        parseProvidersJson(prefs[Keys.AI_PROVIDERS_JSON])
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun getProvidersOnce(): List<AiProviderProfile> = providersFlow.first()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.focusFlowDataStore.edit { prefs ->
            val current = AppSettings(
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                blockYoutubeApp = prefs[Keys.BLOCK_YOUTUBE_APP] ?: false,
                blockYoutubeAppDuringBreaks = prefs[Keys.BLOCK_YOUTUBE_BREAKS] ?: false,
                blockYoutubeShorts = prefs[Keys.BLOCK_YOUTUBE_SHORTS]?.let { runCatching { ShortsMode.valueOf(it) }.getOrNull() } ?: ShortsMode.DURING_SESSIONS,
                skipYoutubeHomeFeed = prefs[Keys.SKIP_HOME_FEED] ?: false,
                blockAllExceptAllowed = prefs[Keys.BLOCK_ALL_EXCEPT_ALLOWED] ?: false,
                assistantBubbleEnabled = prefs[Keys.ASSISTANT_BUBBLE] ?: true,
                strictDefault = prefs[Keys.STRICT_DEFAULT] ?: true,
                voiceOfflineOnly = prefs[Keys.VOICE_OFFLINE_ONLY] ?: false,
                autoSummaryEnabled = prefs[Keys.AUTO_SUMMARY] ?: true,
                autoLaunchSnapGpt = prefs[Keys.AUTO_LAUNCH_SNAPGPT] ?: false,
                snapGptPackage = prefs[Keys.SNAPGPT_PACKAGE] ?: "com.snapgpt.assistant",
                activeProviderId = prefs[Keys.ACTIVE_PROVIDER_ID]?.ifBlank { null },
                wifiSyncCode = prefs[Keys.WIFI_SYNC_CODE] ?: "FF-4821",
                wifiSyncLastSyncedAt = prefs[Keys.WIFI_SYNC_LAST_AT],
                wifiSyncLastPeer = prefs[Keys.WIFI_SYNC_LAST_PEER]?.ifBlank { null },
                chapterConfidence = parseConfidenceJson(prefs[Keys.CHAPTER_CONFIDENCE_JSON]),
                widgetPresetTasks = parseWidgetTasksJson(prefs[Keys.WIDGET_PRESET_TASKS_JSON]),
                androidWidgetBindings = parseWidgetBindingsJson(prefs[Keys.ANDROID_WIDGET_BINDINGS_JSON]),
                seedVersion = prefs[Keys.SEED_VERSION] ?: 0
            )
            val next = transform(current)
            prefs[Keys.THEME_MODE] = next.themeMode.name
            prefs[Keys.BLOCK_YOUTUBE_APP] = next.blockYoutubeApp
            prefs[Keys.BLOCK_YOUTUBE_BREAKS] = next.blockYoutubeAppDuringBreaks
            prefs[Keys.BLOCK_YOUTUBE_SHORTS] = next.blockYoutubeShorts.name
            prefs[Keys.SKIP_HOME_FEED] = next.skipYoutubeHomeFeed
            prefs[Keys.BLOCK_ALL_EXCEPT_ALLOWED] = next.blockAllExceptAllowed
            prefs[Keys.ASSISTANT_BUBBLE] = next.assistantBubbleEnabled
            prefs[Keys.STRICT_DEFAULT] = next.strictDefault
            prefs[Keys.VOICE_OFFLINE_ONLY] = next.voiceOfflineOnly
            prefs[Keys.AUTO_SUMMARY] = next.autoSummaryEnabled
            prefs[Keys.AUTO_LAUNCH_SNAPGPT] = next.autoLaunchSnapGpt
            prefs[Keys.SNAPGPT_PACKAGE] = next.snapGptPackage
            if (next.activeProviderId != null) {
                prefs[Keys.ACTIVE_PROVIDER_ID] = next.activeProviderId
            } else {
                prefs.remove(Keys.ACTIVE_PROVIDER_ID)
            }
            prefs[Keys.WIFI_SYNC_CODE] = next.wifiSyncCode
            if (next.wifiSyncLastSyncedAt != null) {
                prefs[Keys.WIFI_SYNC_LAST_AT] = next.wifiSyncLastSyncedAt
            } else {
                prefs.remove(Keys.WIFI_SYNC_LAST_AT)
            }
            if (next.wifiSyncLastPeer != null) {
                prefs[Keys.WIFI_SYNC_LAST_PEER] = next.wifiSyncLastPeer
            } else {
                prefs.remove(Keys.WIFI_SYNC_LAST_PEER)
            }
            prefs[Keys.CHAPTER_CONFIDENCE_JSON] = JSONObject().apply {
                next.chapterConfidence.forEach { (k, v) -> put(k.toString(), v) }
            }.toString()
            prefs[Keys.WIDGET_PRESET_TASKS_JSON] = encodeWidgetTasksJson(next.widgetPresetTasks)
            prefs[Keys.ANDROID_WIDGET_BINDINGS_JSON] = JSONObject().apply {
                next.androidWidgetBindings.forEach { (k, v) -> put(k.toString(), v) }
            }.toString()
            prefs[Keys.SEED_VERSION] = next.seedVersion
        }
    }

    suspend fun saveProviders(providers: List<AiProviderProfile>) {
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("type", p.type.name)
                put("baseUrl", p.baseUrl)
                put("model", p.model)
                put("apiKey", p.apiKey)
            })
        }
        context.focusFlowDataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDERS_JSON] = arr.toString()
        }
    }

    private fun parseConfidenceJson(raw: String?): Map<Long, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val chId = key.toLongOrNull() ?: return@forEach
                    val stars = obj.optInt(key, 0).coerceIn(1, 5)
                    put(chId, stars)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun parseWidgetTasksJson(raw: String?): Map<Long, List<WidgetPresetTask>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val wId = key.toLongOrNull() ?: return@forEach
                    val arr = obj.optJSONArray(key) ?: return@forEach
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val title = item.optString("title").trim()
                            if (title.isNotEmpty()) {
                                add(WidgetPresetTask(title = title, estimatedMinutes = item.optInt("estimatedMinutes", 25).coerceIn(5, 240)))
                            }
                        }
                    }
                    if (list.isNotEmpty()) put(wId, list)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeWidgetTasksJson(map: Map<Long, List<WidgetPresetTask>>): String {
        val obj = JSONObject()
        map.forEach { (wId, list) ->
            val arr = JSONArray()
            list.forEach { task ->
                arr.put(JSONObject().apply {
                    put("title", task.title)
                    put("estimatedMinutes", task.estimatedMinutes)
                })
            }
            obj.put(wId.toString(), arr)
        }
        return obj.toString()
    }

    private fun parseWidgetBindingsJson(raw: String?): Map<Int, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val appWidgetId = key.toIntOrNull() ?: return@forEach
                    val widgetId = obj.optLong(key, -1L)
                    if (widgetId > 0L) put(appWidgetId, widgetId)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun parseProvidersJson(raw: String?): List<AiProviderProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val type = runCatching {
                        AiProviderType.valueOf(obj.optString("type", AiProviderType.OPENAI.name))
                    }.getOrDefault(AiProviderType.OPENAI)
                    add(
                        AiProviderProfile(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            type = type,
                            baseUrl = obj.optString("baseUrl"),
                            model = obj.optString("model"),
                            apiKey = obj.optString("apiKey")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
