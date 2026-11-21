package com.example.data

import com.example.data.local.BreakConfigEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.DashboardWidgetEntity
import com.example.data.local.SubjectEntity
import com.example.model.AppCategory
import com.example.model.CatalogApp
import com.example.model.FocusMode
import com.example.model.ProgressRange
import com.example.model.StudyQuote
import com.example.model.WidgetAccent
import com.example.model.WidgetAction
import com.example.model.WidgetIcon
import com.example.model.WidgetSize
import com.example.model.WidgetType

object SeedData {
    const val CURRENT_SEED_VERSION = 1

    val BUILT_IN_SUBJECTS = listOf(
        "Mathematics",
        "Physics",
        "Chemistry",
        "Biology",
        "Other"
    )

    val CLASS_11_CHAPTERS: Map<String, List<String>> = mapOf(
        "Mathematics" to listOf(
            "Sets",
            "Relations and Functions",
            "Trigonometric Functions",
            "Complex Numbers and Quadratic Equations",
            "Linear Inequalities",
            "Permutations and Combinations",
            "Binomial Theorem",
            "Sequences and Series",
            "Straight Lines",
            "Conic Sections",
            "Introduction to Three Dimensional Geometry",
            "Limits and Derivatives",
            "Statistics",
            "Probability"
        ),
        "Physics" to listOf(
            "Units and Measurements",
            "Motion in a Straight Line",
            "Motion in a Plane",
            "Laws of Motion",
            "Work, Energy and Power",
            "System of Particles and Rotational Motion",
            "Gravitation",
            "Mechanical Properties of Solids",
            "Mechanical Properties of Fluids",
            "Thermal Properties of Matter",
            "Thermodynamics",
            "Kinetic Theory",
            "Oscillations",
            "Waves"
        ),
        "Chemistry" to listOf(
            "Some Basic Concepts of Chemistry",
            "Structure of Atom",
            "Classification of Elements and Periodicity in Properties",
            "Chemical Bonding and Molecular Structure",
            "Thermodynamics",
            "Equilibrium",
            "Redox Reactions",
            "Organic Chemistry: Some Basic Principles and Techniques",
            "Hydrocarbons"
        )
    )

    fun defaultBreakConfig(): BreakConfigEntity = BreakConfigEntity(
        id = 1,
        enabled = true,
        tasksForShortBreak = 1,
        shortBreakMinutes = 5,
        tasksForLongBreak = 3,
        longBreakMinutes = 15
    )

    fun defaultWidgets(subjects: List<SubjectEntity>): List<DashboardWidgetEntity> {
        val mathId = subjects.find { it.name.equals("Mathematics", ignoreCase = true) }?.id
        val physicsId = subjects.find { it.name.equals("Physics", ignoreCase = true) }?.id
        return listOf(
            DashboardWidgetEntity(
                title = "Focus 25",
                type = WidgetType.TIMER,
                action = WidgetAction.START_SESSION,
                icon = WidgetIcon.TIMER,
                accent = WidgetAccent.INDIGO,
                size = WidgetSize.SMALL,
                mode = FocusMode.TIMED,
                durationMinutes = 25,
                strictMode = true,
                sortOrder = 0
            ),
            DashboardWidgetEntity(
                title = "Open focus",
                type = WidgetType.OPEN_ENDED,
                action = WidgetAction.START_SESSION,
                icon = WidgetIcon.PLAY,
                accent = WidgetAccent.TEAL,
                size = WidgetSize.SMALL,
                mode = FocusMode.OPEN_ENDED,
                durationMinutes = null,
                strictMode = true,
                sortOrder = 1
            ),
            DashboardWidgetEntity(
                title = "Mathematics",
                type = WidgetType.SUBJECT,
                action = WidgetAction.START_SESSION,
                icon = WidgetIcon.CALCULATE,
                accent = WidgetAccent.AMBER,
                size = WidgetSize.SMALL,
                mode = FocusMode.TIMED,
                durationMinutes = 60,
                strictMode = true,
                subjectId = mathId,
                sortOrder = 2
            ),
            DashboardWidgetEntity(
                title = "Physics",
                type = WidgetType.SUBJECT,
                action = WidgetAction.START_SESSION,
                icon = WidgetIcon.SCIENCE,
                accent = WidgetAccent.ROSE,
                size = WidgetSize.SMALL,
                mode = FocusMode.TIMED,
                durationMinutes = 60,
                strictMode = true,
                subjectId = physicsId,
                sortOrder = 3
            ),
            DashboardWidgetEntity(
                title = "Study progress",
                type = WidgetType.PROGRESS,
                action = WidgetAction.OPEN_STATISTICS,
                icon = WidgetIcon.TROPHY,
                accent = WidgetAccent.TEAL,
                size = WidgetSize.LARGE,
                mode = FocusMode.TIMED,
                durationMinutes = 25,
                strictMode = true,
                progressRange = ProgressRange.DAYS,
                sortOrder = 4
            )
        )
    }

    val CATALOG_APPS: List<CatalogApp> = listOf(
        // SOCIAL
        CatalogApp("com.instagram.android", "Instagram", AppCategory.SOCIAL),
        CatalogApp("com.zhiliaoapp.musically", "TikTok", AppCategory.SOCIAL),
        CatalogApp("com.snapchat.android", "Snapchat", AppCategory.SOCIAL),
        CatalogApp("com.twitter.android", "X (Twitter)", AppCategory.SOCIAL),
        CatalogApp("com.reddit.frontpage", "Reddit", AppCategory.SOCIAL),
        CatalogApp("com.discord", "Discord", AppCategory.SOCIAL),
        CatalogApp("com.facebook.katana", "Facebook", AppCategory.SOCIAL),
        CatalogApp("com.instagram.barcelona", "Threads", AppCategory.SOCIAL),
        CatalogApp("com.pinterest", "Pinterest", AppCategory.SOCIAL),
        // ENTERTAINMENT
        CatalogApp("com.google.android.youtube", "YouTube", AppCategory.ENTERTAINMENT),
        CatalogApp("com.netflix.mediaclient", "Netflix", AppCategory.ENTERTAINMENT),
        CatalogApp("tv.twitch.android.app", "Twitch", AppCategory.ENTERTAINMENT),
        CatalogApp("com.google.android.apps.youtube.music", "YouTube Music", AppCategory.ENTERTAINMENT),
        CatalogApp("com.spotify.music", "Spotify", AppCategory.ENTERTAINMENT),
        CatalogApp("com.disney.disneyplus", "Disney+", AppCategory.ENTERTAINMENT),
        // GAME
        CatalogApp("com.roblox.client", "Roblox", AppCategory.GAME),
        CatalogApp("com.supercell.brawlstars", "Brawl Stars", AppCategory.GAME),
        CatalogApp("com.supercell.clashroyale", "Clash Royale", AppCategory.GAME),
        CatalogApp("com.mojang.minecraftpe", "Minecraft", AppCategory.GAME),
        CatalogApp("com.innersloth.spacemafia", "Among Us", AppCategory.GAME),
        // CUSTOM / TOOLS
        CatalogApp("com.android.chrome", "Chrome", AppCategory.CUSTOM),
        CatalogApp("com.whatsapp", "WhatsApp", AppCategory.CUSTOM),
        CatalogApp("org.telegram.messenger", "Telegram", AppCategory.CUSTOM),
        CatalogApp("com.google.android.gm", "Gmail", AppCategory.CUSTOM),
        CatalogApp("com.google.android.apps.docs", "Google Docs", AppCategory.CUSTOM),
        CatalogApp("com.snapgpt.assistant", "SnapGPT", AppCategory.CUSTOM)
    )

    val STUDY_QUOTES: List<StudyQuote> = listOf(
        StudyQuote("Deep Focus", "Success is the sum of small efforts, repeated day in and day out.", "Robert Collier"),
        StudyQuote("Consistency", "Discipline is choosing between what you want now and what you want most.", "Abraham Lincoln"),
        StudyQuote("Time Management", "You do not rise to the level of your goals. You fall to the level of your systems.", "James Clear"),
        StudyQuote("Persistence", "It always seems impossible until it is done.", "Nelson Mandela"),
        StudyQuote("Curiosity", "The expert in anything was once a beginner.", "Helen Hayes"),
        StudyQuote("Momentum", "Start where you are. Use what you have. Do what you can.", "Arthur Ashe"),
        StudyQuote("Learning", "Tell me and I forget, teach me and I may remember, involve me and I learn.", "Benjamin Franklin"),
        StudyQuote("Mastery", "Amateurs sit and wait for inspiration, the rest of us just get up and go to work.", "Stephen King"),
        StudyQuote("Study Systems", "Focus is saying no to a hundred other good ideas.", "Steve Jobs"),
        StudyQuote("Discipline", "Small daily improvements over time lead to stunning results.", "Robin Sharma"),
        StudyQuote("Single-Tasking", "Clarity about what matters provides clarity about what does not.", "Cal Newport"),
        StudyQuote("Execution", "Action is the foundational key to all success.", "Pablo Picasso")
    )
}
