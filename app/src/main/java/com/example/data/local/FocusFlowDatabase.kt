package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SubjectEntity::class,
        ChapterEntity::class,
        StudyTaskEntity::class,
        SubtaskEntity::class,
        FocusSessionEntity::class,
        SessionChapterEntity::class,
        SessionTaskEntity::class,
        PresetEntity::class,
        PresetChapterEntity::class,
        PresetTaskEntity::class,
        BlockedAppEntity::class,
        AllowedAppEntity::class,
        BreakConfigEntity::class,
        SessionSummaryEntity::class,
        DashboardWidgetEntity::class,
        CustomCatalogAppEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(FocusFlowConverters::class)
abstract class FocusFlowDatabase : RoomDatabase() {
    abstract fun dao(): FocusFlowDao

    companion object {
        @Volatile
        private var INSTANCE: FocusFlowDatabase? = null

        fun getInstance(context: Context): FocusFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusFlowDatabase::class.java,
                    "focusflow_room.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
