package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.model.AppCategory
import com.example.model.FocusMode
import com.example.model.ProgressRange
import com.example.model.SessionStatus
import com.example.model.TaskOrigin
import com.example.model.TaskStatus
import com.example.model.WidgetAccent
import com.example.model.WidgetAction
import com.example.model.WidgetIcon
import com.example.model.WidgetSize
import com.example.model.WidgetType

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val builtIn: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val subjectId: Long,
    val name: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val estimatedMinutes: Int = 25,
    val status: TaskStatus = TaskStatus.PENDING,
    val origin: TaskOrigin = TaskOrigin.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "subtasks")
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val mode: FocusMode = FocusMode.TIMED,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val plannedDurationMinutes: Int? = 25,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val breakMsCompleted: Long = 0L,
    val breakStartedAt: Long? = null,
    val breakEndsAt: Long? = null,
    val shortBreaksUsed: Int = 0,
    val longBreaksUsed: Int = 0,
    val strictMode: Boolean = true,
    val rawStudyPlan: String = "",
    val widgetId: Long? = null
)

@Entity(tableName = "session_chapters")
data class SessionChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val subjectId: Long,
    val chapterId: Long? = null
)

@Entity(tableName = "session_tasks")
data class SessionTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val studyTaskId: Long? = null,
    val title: String,
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val estimatedMinutes: Int = 25,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Int = 0
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val mode: FocusMode = FocusMode.TIMED,
    val plannedDurationMinutes: Int? = 25,
    val strictMode: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "preset_chapters")
data class PresetChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val presetId: Long,
    val subjectId: Long,
    val chapterId: Long? = null
)

@Entity(tableName = "preset_tasks")
data class PresetTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val presetId: Long,
    val title: String,
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val estimatedMinutes: Int = 25,
    val sortOrder: Int = 0
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.SOCIAL,
    val blockedDuringBreak: Boolean = false
)

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.CUSTOM
)

@Entity(tableName = "break_config")
data class BreakConfigEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
    val tasksForShortBreak: Int = 1,
    val shortBreakMinutes: Int = 5,
    val tasksForLongBreak: Int = 3,
    val longBreakMinutes: Int = 15
)

@Entity(tableName = "session_summaries")
data class SessionSummaryEntity(
    @PrimaryKey val sessionId: Long,
    val summaryText: String,
    val providerUsed: String,
    val generatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dashboard_widgets")
data class DashboardWidgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val type: WidgetType = WidgetType.TIMER,
    val action: WidgetAction = WidgetAction.START_SESSION,
    val icon: WidgetIcon = WidgetIcon.TIMER,
    val accent: WidgetAccent = WidgetAccent.INDIGO,
    val size: WidgetSize = WidgetSize.SMALL,
    val mode: FocusMode = FocusMode.TIMED,
    val durationMinutes: Int? = 25,
    val strictMode: Boolean = true,
    val subjectId: Long? = null,
    val progressRange: ProgressRange = ProgressRange.DAYS,
    val sortOrder: Int = 0
)

@Entity(tableName = "custom_catalog_apps")
data class CustomCatalogAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.CUSTOM
)

class FocusFlowConverters {
    @TypeConverter fun fromFocusMode(value: FocusMode): String = value.name
    @TypeConverter fun toFocusMode(value: String): FocusMode = runCatching { FocusMode.valueOf(value) }.getOrDefault(FocusMode.TIMED)

    @TypeConverter fun fromSessionStatus(value: SessionStatus): String = value.name
    @TypeConverter fun toSessionStatus(value: String): SessionStatus = runCatching { SessionStatus.valueOf(value) }.getOrDefault(SessionStatus.COMPLETED)

    @TypeConverter fun fromTaskOrigin(value: TaskOrigin): String = value.name
    @TypeConverter fun toTaskOrigin(value: String): TaskOrigin = runCatching { TaskOrigin.valueOf(value) }.getOrDefault(TaskOrigin.MANUAL)

    @TypeConverter fun fromTaskStatus(value: TaskStatus): String = value.name
    @TypeConverter fun toTaskStatus(value: String): TaskStatus = runCatching { TaskStatus.valueOf(value) }.getOrDefault(TaskStatus.PENDING)

    @TypeConverter fun fromWidgetType(value: WidgetType): String = value.name
    @TypeConverter fun toWidgetType(value: String): WidgetType = runCatching { WidgetType.valueOf(value) }.getOrDefault(WidgetType.TIMER)

    @TypeConverter fun fromWidgetAction(value: WidgetAction): String = value.name
    @TypeConverter fun toWidgetAction(value: String): WidgetAction = runCatching { WidgetAction.valueOf(value) }.getOrDefault(WidgetAction.START_SESSION)

    @TypeConverter fun fromWidgetIcon(value: WidgetIcon): String = value.name
    @TypeConverter fun toWidgetIcon(value: String): WidgetIcon = runCatching { WidgetIcon.valueOf(value) }.getOrDefault(WidgetIcon.TIMER)

    @TypeConverter fun fromWidgetAccent(value: WidgetAccent): String = value.name
    @TypeConverter fun toWidgetAccent(value: String): WidgetAccent = runCatching { WidgetAccent.valueOf(value) }.getOrDefault(WidgetAccent.INDIGO)

    @TypeConverter fun fromWidgetSize(value: WidgetSize): String = value.name
    @TypeConverter fun toWidgetSize(value: String): WidgetSize = runCatching { WidgetSize.valueOf(value) }.getOrDefault(WidgetSize.SMALL)

    @TypeConverter fun fromProgressRange(value: ProgressRange): String = value.name
    @TypeConverter fun toProgressRange(value: String): ProgressRange = runCatching { ProgressRange.valueOf(value) }.getOrDefault(ProgressRange.DAYS)

    @TypeConverter fun fromAppCategory(value: AppCategory): String = value.name
    @TypeConverter fun toAppCategory(value: String): AppCategory = runCatching { AppCategory.valueOf(value) }.getOrDefault(AppCategory.CUSTOM)
}
