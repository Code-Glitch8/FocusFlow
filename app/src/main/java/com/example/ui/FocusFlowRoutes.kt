package com.example.ui

enum class TopTab(val label: String) {
    HOME("Home"),
    TASKS("Tasks"),
    FOCUS("Focus"),
    STATISTICS("Statistics"),
    SETTINGS("Settings")
}

sealed class FocusRoute {
    data class Tab(val tab: TopTab) : FocusRoute()
    data object Widgets : FocusRoute()
    data object Subjects : FocusRoute()
    data class SessionPrep(
        val title: String = "Custom session",
        val mode: com.example.model.FocusMode = com.example.model.FocusMode.TIMED,
        val durationMinutes: Int = 25,
        val strictMode: Boolean = true,
        val initialSubjectId: Long? = null,
        val widgetId: Long? = null,
        val openVoiceOnLaunch: Boolean = false
    ) : FocusRoute()
    data class SessionSummary(val sessionId: Long) : FocusRoute()
    data object History : FocusRoute()
    data object Blocking : FocusRoute()
    data object DetectorTest : FocusRoute()
    data object AiSettings : FocusRoute()
    data object AiProviders : FocusRoute()
    data object DataSettings : FocusRoute()
}
