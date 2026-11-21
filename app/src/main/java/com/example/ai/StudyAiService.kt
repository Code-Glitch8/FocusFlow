package com.example.ai

import android.util.Log
import com.example.data.local.ChapterEntity
import com.example.data.local.SubjectEntity
import com.example.domain.AiGrounding
import com.example.domain.StudyPlanParser
import com.example.model.AiExtractResult
import com.example.model.AiProviderProfile
import com.example.model.AiProviderType
import com.example.model.AiSummaryResult
import com.example.model.DraftStudyTask
import com.example.model.SubjectPlan
import com.example.model.SubtaskDraft
import com.example.model.TaskOrigin
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "StudyAiService"
private const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"

class StudyAiService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val extractSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "tasks" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Concise study task title derived from the student's plan"),
                            "minutes" to Schema.integer(description = "Estimated duration in minutes, 10 to 240"),
                            "subtasks" to Schema.array(
                                items = Schema.string(),
                                description = "Optional 0-3 short actionable subtasks"
                            )
                        ),
                        optionalProperties = listOf("subtasks")
                    ),
                    description = "Actionable study tasks grounded in the student's input"
                )
            )
        )
    }

    private val firebaseStructuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractSchema
            }
        )
    }

    private val firebaseTextModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL
        )
    }

    suspend fun extractTasks(
        studentPlan: String,
        subjectPlans: List<SubjectPlan>,
        subjects: List<SubjectEntity>,
        chapters: List<ChapterEntity>,
        activeProvider: AiProviderProfile?,
        origin: TaskOrigin = TaskOrigin.AI_TEXT
    ): AiExtractResult {
        val localBaseline = StudyPlanParser.parseLocal(
            rawPlan = studentPlan,
            subjectPlans = subjectPlans,
            subjects = subjects,
            chapters = chapters,
            origin = origin
        )

        val prompt = buildExtractPrompt(studentPlan, subjectPlans)

        return try {
            val (rawJson, providerLabel) = if (activeProvider != null && activeProvider.type != AiProviderType.FIREBASE_AI) {
                if (activeProvider.apiKey.isBlank()) {
                    throw IllegalStateException("${activeProvider.name} has no API key configured.")
                }
                callCustomProvider(activeProvider, prompt, expectJson = true) to activeProvider.name
            } else {
                val response = withTimeout(60_000L) {
                    firebaseStructuredModel.generateContent(prompt)
                }
                val text = response.text
                if (text.isNullOrBlank()) {
                    throw IllegalStateException("Gemini returned an empty response.")
                }
                text to "Built-in Gemini AI ($DEFAULT_GEMINI_MODEL)"
            }

            val parsed = parseTasksJson(rawJson, subjectPlans, subjects, chapters, origin)
            val grounded = AiGrounding.keepGrounded(parsed, studentPlan, subjectPlans, subjects, chapters)
            val finalTasks = grounded.ifEmpty { localBaseline }

            AiExtractResult(
                tasks = finalTasks,
                providerUsed = providerLabel,
                warning = if (grounded.isEmpty() && localBaseline.isNotEmpty()) {
                    "Filtered ungrounded suggestions and kept local parser matches."
                } else null
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Task extraction timed out", e)
            throw IllegalStateException("AI request timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Task extraction failed", e)
            throw e
        }
    }

    suspend fun generateSessionSummary(
        factsPrompt: String,
        activeProvider: AiProviderProfile?
    ): AiSummaryResult {
        return try {
            val (rawText, providerLabel) = if (activeProvider != null && activeProvider.type != AiProviderType.FIREBASE_AI) {
                if (activeProvider.apiKey.isBlank()) {
                    throw IllegalStateException("${activeProvider.name} has no API key configured.")
                }
                callCustomProvider(activeProvider, factsPrompt, expectJson = false) to activeProvider.name
            } else {
                val response = withTimeout(60_000L) {
                    firebaseTextModel.generateContent(factsPrompt)
                }
                val text = response.text?.trim()
                if (text.isNullOrBlank()) {
                    throw IllegalStateException("Gemini returned an empty summary.")
                }
                text to "Built-in Gemini AI ($DEFAULT_GEMINI_MODEL)"
            }

            val numbersOk = AiGrounding.numbersMatchFacts(rawText, factsPrompt)
            AiSummaryResult(
                summary = rawText.trim(),
                providerUsed = providerLabel,
                warning = if (!numbersOk) "Review numbers carefully — some figures may be estimates." else null
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Summary generation timed out", e)
            throw IllegalStateException("Summary generation timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed", e)
            throw e
        }
    }

    suspend fun testProviderConnection(provider: AiProviderProfile?): String {
        val prompt = "Reply with exactly: FocusFlow AI ready."
        return if (provider == null || provider.type == AiProviderType.FIREBASE_AI) {
            val response = withTimeout(60_000L) {
                firebaseTextModel.generateContent(prompt)
            }
            response.text?.trim().orEmpty().ifBlank { "Connected to Firebase AI Logic." }
        } else {
            if (provider.apiKey.isBlank()) {
                throw IllegalStateException("Enter an API key first.")
            }
            callCustomProvider(provider, prompt, expectJson = false)
        }
    }

    private fun buildExtractPrompt(studentPlan: String, subjectPlans: List<SubjectPlan>): String {
        val contextLines = if (subjectPlans.isNotEmpty()) {
            subjectPlans.joinToString("\n") { "- ${it.subjectName}: ${it.chaptersSummary.ifBlank { "general" }}" }
        } else {
            "- General study"
        }
        return """
            Extract concrete, actionable study tasks strictly from the student's study plan below.
            Do NOT invent topics or chapters that were not mentioned in the student's input or subject context.
            Selected subjects and chapters:
            $contextLines

            Student's study plan:
            "$studentPlan"

            Return JSON matching the schema with an array of tasks, each having title, estimated minutes (10-240), and optional short subtasks.
        """.trimIndent()
    }

    private fun parseTasksJson(
        rawJson: String,
        subjectPlans: List<SubjectPlan>,
        subjects: List<SubjectEntity>,
        chapters: List<ChapterEntity>,
        origin: TaskOrigin
    ): List<DraftStudyTask> {
        val trimmed = rawJson.trim()
        val obj = JSONObject(trimmed)
        val arr = obj.optJSONArray("tasks") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isBlank()) continue
                val mins = item.optInt("minutes", 25).coerceIn(10, 240)
                val matchedLocal = StudyPlanParser.parseLocal(title, subjectPlans, subjects, chapters, origin).firstOrNull()
                val subsArr = item.optJSONArray("subtasks") ?: JSONArray()
                val subtasks = (0 until subsArr.length()).mapNotNull { j ->
                    subsArr.optString(j).trim().takeIf { it.isNotEmpty() }?.let {
                        SubtaskDraft(UUID.randomUUID().toString(), it)
                    }
                }
                add(
                    DraftStudyTask(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        estimatedMinutes = mins,
                        subjectId = matchedLocal?.subjectId ?: subjectPlans.firstOrNull()?.subjectId,
                        chapterId = matchedLocal?.chapterId,
                        subtasks = subtasks,
                        origin = origin
                    )
                )
            }
        }
    }

    private suspend fun callCustomProvider(
        provider: AiProviderProfile,
        prompt: String,
        expectJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        withTimeout(60_000L) {
            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            when (provider.type) {
                AiProviderType.GEMINI -> {
                    val model = provider.model.ifBlank { DEFAULT_GEMINI_MODEL }
                    val base = provider.baseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }.trimEnd('/')
                    val url = "$base/models/$model:generateContent?key=${provider.apiKey.trim()}"
                    val bodyObj = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                        }))
                        if (expectJson) {
                            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
                        }
                    }
                    val req = Request.Builder()
                        .url(url)
                        .post(bodyObj.toString().toRequestBody(jsonMedia))
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}: $respBody")
                    }
                    val parsed = JSONObject(respBody)
                    parsed.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
                else -> {
                    val base = when (provider.type) {
                        AiProviderType.OPENROUTER -> provider.baseUrl.ifBlank { "https://openrouter.ai/api/v1" }
                        AiProviderType.OPENAI -> provider.baseUrl.ifBlank { "https://api.openai.com/v1" }
                        else -> provider.baseUrl
                    }.trimEnd('/')
                    require(base.isNotBlank()) { "Base URL is required for OpenAI-compatible providers." }
                    val model = provider.model.ifBlank {
                        if (provider.type == AiProviderType.OPENROUTER) "openai/gpt-4o-mini" else "gpt-4o-mini"
                    }
                    val bodyObj = JSONObject().apply {
                        put("model", model)
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }))
                        if (expectJson) {
                            put("response_format", JSONObject().put("type", "json_object"))
                        }
                    }
                    val req = Request.Builder()
                        .url("$base/chat/completions")
                        .header("Authorization", "Bearer ${provider.apiKey.trim()}")
                        .post(bodyObj.toString().toRequestBody(jsonMedia))
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}: $respBody")
                    }
                    val parsed = JSONObject(respBody)
                    parsed.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
        }
    }
}
