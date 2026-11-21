package com.example.model

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class FocusMode(val label: String) {
    TIMED("Timed"),
    OPEN_ENDED("Open-ended")
}

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED
}

enum class TaskOrigin {
    MANUAL,
    AI_TEXT,
    AI_VOICE
}

enum class TaskStatus {
    PENDING,
    DONE
}

enum class WidgetType(val label: String) {
    TIMER("Timer"),
    OPEN_ENDED("Open focus"),
    SUBJECT("Subject"),
    PROGRESS("Study progress"),
    QUOTE("Study quote")
}

enum class WidgetAction(val label: String) {
    START_SESSION("Start session"),
    OPEN_STATISTICS("Open statistics"),
    OPEN_AI_SETUP("Voice / AI setup")
}

enum class WidgetSize(val label: String) {
    SMALL("Compact tile"),
    LARGE("Full-width card")
}

enum class ProgressRange(val label: String) {
    DAYS("Past days"),
    WEEKS("Past weeks"),
    MONTH("Past month")
}

enum class WidgetAccent(val key: String, val label: String) {
    INDIGO("indigo", "Indigo"),
    TEAL("teal", "Teal"),
    AMBER("amber", "Amber"),
    ROSE("rose", "Rose"),
    GREEN("green", "Green"),
    SLATE("slate", "Slate")
}

enum class WidgetIcon(val key: String, val label: String) {
    TIMER("timer", "Timer"),
    PLAY("play", "Play"),
    BOOK("book", "Book"),
    CALCULATE("calculate", "Math"),
    SCIENCE("science", "Science"),
    BIOTECH("biotech", "Biology"),
    BOLT("bolt", "Bolt"),
    EDIT("edit", "Edit"),
    STAR("star", "Star"),
    SCHOOL("school", "School"),
    ALARM("alarm", "Alarm"),
    BOOKMARK("bookmark", "Bookmark"),
    PSYCHOLOGY("psychology", "Mind"),
    CODE("code", "Code"),
    LANGUAGE("language", "Language"),
    TROPHY("trophy", "Progress")
}

enum class ShortsMode(val label: String) {
    OFF("Off"),
    DURING_SESSIONS("During sessions"),
    ALWAYS("Always")
}

enum class AppCategory(val label: String) {
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    GAME("Games"),
    CUSTOM("Tools & Other")
}

enum class AiProviderType(val label: String) {
    FIREBASE_AI("Built-in Gemini AI"),
    OPENAI("OpenAI"),
    GEMINI("Gemini (Custom Key)"),
    OPENROUTER("OpenRouter"),
    OPENAI_COMPATIBLE("OpenAI-Compatible")
}

enum class YouTubeScreenKind {
    SHORTS_PLAYER,
    HOME_FEED,
    REGULAR_YOUTUBE
}

data class UiNode(
    val viewId: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val selected: Boolean = false,
    val children: List<UiNode> = emptyList()
)

data class YouTubeDetectionResult(
    val kind: YouTubeScreenKind,
    val reason: String,
    val matchedId: String? = null,
    val sampledViewIds: List<String> = emptyList()
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val blockYoutubeApp: Boolean = false,
    val blockYoutubeAppDuringBreaks: Boolean = false,
    val blockYoutubeShorts: ShortsMode = ShortsMode.DURING_SESSIONS,
    val skipYoutubeHomeFeed: Boolean = false,
    val blockAllExceptAllowed: Boolean = false,
    val assistantBubbleEnabled: Boolean = true,
    val strictDefault: Boolean = true,
    val voiceOfflineOnly: Boolean = false,
    val autoSummaryEnabled: Boolean = true,
    val autoLaunchSnapGpt: Boolean = false,
    val snapGptPackage: String = "com.snapgpt.assistant",
    val activeProviderId: String? = null,
    val wifiSyncCode: String = "FF-4821",
    val wifiSyncLastSyncedAt: Long? = null,
    val wifiSyncLastPeer: String? = null,
    val chapterConfidence: Map<Long, Int> = emptyMap(),
    val widgetPresetTasks: Map<Long, List<WidgetPresetTask>> = emptyMap(),
    val androidWidgetBindings: Map<Int, Long> = emptyMap(),
    val seedVersion: Int = 0
)

data class WidgetPresetTask(
    val title: String,
    val estimatedMinutes: Int = 25
)

data class ChapterProgressItem(
    val chapterId: Long,
    val subjectId: Long,
    val chapterName: String,
    val subjectName: String,
    val weekMinutes: Int,
    val totalMinutes: Int,
    val doneTasks: Int,
    val openTasks: Int
)

data class AiProviderProfile(
    val id: String,
    val name: String,
    val type: AiProviderType,
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = ""
)

data class SubtaskDraft(
    val id: String,
    val title: String
)

data class DraftStudyTask(
    val id: String,
    val title: String,
    val estimatedMinutes: Int,
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val subtasks: List<SubtaskDraft> = emptyList(),
    val origin: TaskOrigin = TaskOrigin.AI_TEXT,
    val dedupeKey: String = ""
)

data class SubjectPlan(
    val subjectId: Long,
    val subjectName: String,
    val chapterIds: List<Long> = emptyList(),
    val chaptersSummary: String = ""
)

data class StudyQuote(
    val topic: String,
    val quote: String,
    val author: String
)

data class BlockDecision(
    val shouldBlock: Boolean,
    val reason: String,
    val packageName: String,
    val appLabel: String,
    val isYoutubeShorts: Boolean = false
)

data class EarnedBreakSnapshot(
    val shortEarned: Int,
    val longEarned: Int,
    val shortAvailable: Int,
    val longAvailable: Int,
    val nextShortInTasks: Int?,
    val nextLongInTasks: Int?
)

data class SessionTimeSnapshot(
    val focusElapsedMs: Long,
    val breakElapsedMs: Long,
    val totalPlannedMs: Long?,
    val remainingMs: Long?,
    val isOnBreak: Boolean,
    val breakRemainingMs: Long,
    val isTimedFinished: Boolean
)

data class StudyStats(
    val totalFocusMinutes: Int,
    val todayFocusMinutes: Int,
    val last7DaysMinutes: Int = 0,
    val totalCompletedTasks: Int,
    val totalOpenTasks: Int = 0,
    val todayCompletedTasks: Int,
    val streakDays: Int,
    val totalSessions: Int,
    val dailyMinutes: List<Pair<String, Int>>,
    val weeklyMinutes: List<Pair<String, Int>>,
    val monthlyMinutes: List<Pair<String, Int>>,
    val subjectBreakdown: List<Pair<String, Int>>,
    val chaptersThisWeek: List<ChapterProgressItem> = emptyList(),
    val allChapterProgress: List<ChapterProgressItem> = emptyList()
)

data class CatalogApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val isCustom: Boolean = false
)

data class AiExtractResult(
    val tasks: List<DraftStudyTask>,
    val providerUsed: String,
    val warning: String? = null
)

data class AiSummaryResult(
    val summary: String,
    val providerUsed: String,
    val warning: String? = null
)

data class PermissionHealth(
    val accessibilityEnabled: Boolean,
    val usageStatsGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
    val microphoneGranted: Boolean
)

data class ActiveShieldAlert(
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val isYoutubeShorts: Boolean,
    val isYoutubeApp: Boolean,
    val triggeredAt: Long = System.currentTimeMillis()
)
