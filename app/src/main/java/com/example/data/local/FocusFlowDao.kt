package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusFlowDao {

    // Subjects
    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, id ASC")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, id ASC")
    suspend fun getSubjectsOnce(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustomSubject(id: Long)

    // Chapters
    @Query("SELECT * FROM chapters ORDER BY subjectId ASC, sortOrder ASC, id ASC")
    fun observeChapters(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters ORDER BY subjectId ASC, sortOrder ASC, id ASC")
    suspend fun getChaptersOnce(): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapter(id: Long)

    @Query("DELETE FROM chapters WHERE subjectId = :subjectId")
    suspend fun deleteChaptersForSubject(subjectId: Long)

    // Study Tasks
    @Query("SELECT * FROM study_tasks ORDER BY status ASC, createdAt DESC")
    fun observeStudyTasks(): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks ORDER BY createdAt DESC")
    suspend fun getStudyTasksOnce(): List<StudyTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyTask(task: StudyTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyTasks(tasks: List<StudyTaskEntity>)

    @Update
    suspend fun updateStudyTask(task: StudyTaskEntity)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteStudyTask(id: Long)

    // Subtasks
    @Query("SELECT * FROM subtasks ORDER BY taskId ASC, sortOrder ASC, id ASC")
    fun observeSubtasks(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks ORDER BY taskId ASC, sortOrder ASC, id ASC")
    suspend fun getSubtasksOnce(): List<SubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtask(subtask: SubtaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasksForTask(taskId: Long)

    // Focus Sessions
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    suspend fun getSessionsOnce(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<FocusSessionEntity>)

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // Session Chapters
    @Query("SELECT * FROM session_chapters ORDER BY id ASC")
    fun observeSessionChapters(): Flow<List<SessionChapterEntity>>

    @Query("SELECT * FROM session_chapters ORDER BY id ASC")
    suspend fun getSessionChaptersOnce(): List<SessionChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionChapters(items: List<SessionChapterEntity>)

    @Query("DELETE FROM session_chapters WHERE sessionId = :sessionId")
    suspend fun deleteSessionChapters(sessionId: Long)

    // Session Tasks
    @Query("SELECT * FROM session_tasks ORDER BY sortOrder ASC, id ASC")
    fun observeSessionTasks(): Flow<List<SessionTaskEntity>>

    @Query("SELECT * FROM session_tasks ORDER BY sortOrder ASC, id ASC")
    suspend fun getSessionTasksOnce(): List<SessionTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionTask(task: SessionTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionTasks(tasks: List<SessionTaskEntity>)

    @Update
    suspend fun updateSessionTask(task: SessionTaskEntity)

    @Query("DELETE FROM session_tasks WHERE id = :id")
    suspend fun deleteSessionTask(id: Long)

    @Query("DELETE FROM session_tasks WHERE sessionId = :sessionId")
    suspend fun deleteSessionTasksForSession(sessionId: Long)

    // Presets
    @Query("SELECT * FROM presets ORDER BY createdAt DESC")
    fun observePresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY createdAt DESC")
    suspend fun getPresetsOnce(): List<PresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<PresetEntity>)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePreset(id: Long)

    @Query("SELECT * FROM preset_chapters")
    fun observePresetChapters(): Flow<List<PresetChapterEntity>>

    @Query("SELECT * FROM preset_chapters")
    suspend fun getPresetChaptersOnce(): List<PresetChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresetChapters(items: List<PresetChapterEntity>)

    @Query("DELETE FROM preset_chapters WHERE presetId = :presetId")
    suspend fun deletePresetChapters(presetId: Long)

    @Query("SELECT * FROM preset_tasks ORDER BY sortOrder ASC, id ASC")
    fun observePresetTasks(): Flow<List<PresetTaskEntity>>

    @Query("SELECT * FROM preset_tasks ORDER BY sortOrder ASC, id ASC")
    suspend fun getPresetTasksOnce(): List<PresetTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresetTasks(items: List<PresetTaskEntity>)

    @Query("DELETE FROM preset_tasks WHERE presetId = :presetId")
    suspend fun deletePresetTasks(presetId: Long)

    // Blocked & Allowed Apps
    @Query("SELECT * FROM blocked_apps ORDER BY label ASC")
    fun observeBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps ORDER BY label ASC")
    suspend fun getBlockedAppsOnce(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApps(apps: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteBlockedApp(packageName: String)

    @Query("SELECT * FROM allowed_apps ORDER BY label ASC")
    fun observeAllowedApps(): Flow<List<AllowedAppEntity>>

    @Query("SELECT * FROM allowed_apps ORDER BY label ASC")
    suspend fun getAllowedAppsOnce(): List<AllowedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllowedApp(app: AllowedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllowedApps(apps: List<AllowedAppEntity>)

    @Query("DELETE FROM allowed_apps WHERE packageName = :packageName")
    suspend fun deleteAllowedApp(packageName: String)

    // Break Config
    @Query("SELECT * FROM break_config WHERE id = 1 LIMIT 1")
    fun observeBreakConfig(): Flow<BreakConfigEntity?>

    @Query("SELECT * FROM break_config WHERE id = 1 LIMIT 1")
    suspend fun getBreakConfigOnce(): BreakConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakConfig(config: BreakConfigEntity)

    // Summaries
    @Query("SELECT * FROM session_summaries")
    fun observeSummaries(): Flow<List<SessionSummaryEntity>>

    @Query("SELECT * FROM session_summaries")
    suspend fun getSummariesOnce(): List<SessionSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SessionSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummaries(summaries: List<SessionSummaryEntity>)

    @Query("DELETE FROM session_summaries WHERE sessionId = :sessionId")
    suspend fun deleteSummary(sessionId: Long)

    // Dashboard Widgets
    @Query("SELECT * FROM dashboard_widgets ORDER BY sortOrder ASC, id ASC")
    fun observeWidgets(): Flow<List<DashboardWidgetEntity>>

    @Query("SELECT * FROM dashboard_widgets ORDER BY sortOrder ASC, id ASC")
    suspend fun getWidgetsOnce(): List<DashboardWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWidget(widget: DashboardWidgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWidgets(widgets: List<DashboardWidgetEntity>)

    @Update
    suspend fun updateWidget(widget: DashboardWidgetEntity)

    @Query("DELETE FROM dashboard_widgets WHERE id = :id")
    suspend fun deleteWidget(id: Long)

    // Custom Catalog Apps
    @Query("SELECT * FROM custom_catalog_apps ORDER BY label ASC")
    fun observeCustomCatalogApps(): Flow<List<CustomCatalogAppEntity>>

    @Query("SELECT * FROM custom_catalog_apps ORDER BY label ASC")
    suspend fun getCustomCatalogAppsOnce(): List<CustomCatalogAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCatalogApp(app: CustomCatalogAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCatalogApps(apps: List<CustomCatalogAppEntity>)

    // Clear all tables for backup restore / factory reset
    @Query("DELETE FROM subjects") suspend fun clearSubjects()
    @Query("DELETE FROM chapters") suspend fun clearChapters()
    @Query("DELETE FROM study_tasks") suspend fun clearStudyTasks()
    @Query("DELETE FROM subtasks") suspend fun clearSubtasks()
    @Query("DELETE FROM focus_sessions") suspend fun clearSessions()
    @Query("DELETE FROM session_chapters") suspend fun clearSessionChapters()
    @Query("DELETE FROM session_tasks") suspend fun clearSessionTasks()
    @Query("DELETE FROM presets") suspend fun clearPresets()
    @Query("DELETE FROM preset_chapters") suspend fun clearPresetChapters()
    @Query("DELETE FROM preset_tasks") suspend fun clearPresetTasks()
    @Query("DELETE FROM blocked_apps") suspend fun clearBlockedApps()
    @Query("DELETE FROM allowed_apps") suspend fun clearAllowedApps()
    @Query("DELETE FROM break_config") suspend fun clearBreakConfig()
    @Query("DELETE FROM session_summaries") suspend fun clearSummaries()
    @Query("DELETE FROM dashboard_widgets") suspend fun clearWidgets()
    @Query("DELETE FROM custom_catalog_apps") suspend fun clearCustomCatalogApps()
}
