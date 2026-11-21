package com.example

import com.example.data.SeedData
import com.example.data.local.AllowedAppEntity
import com.example.data.local.BlockedAppEntity
import com.example.data.local.BreakConfigEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.SubjectEntity
import com.example.domain.AiGrounding
import com.example.domain.BlockPolicy
import com.example.domain.EarnedBreakCalculator
import com.example.domain.SessionClock
import com.example.domain.StudyPlanParser
import com.example.domain.YouTubeUiClassifier
import com.example.model.AppCategory
import com.example.model.AppSettings
import com.example.model.FocusMode
import com.example.model.SessionStatus
import com.example.model.ShortsMode
import com.example.model.SubjectPlan
import com.example.model.YouTubeScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusFlowLogicTest {

    @Test
    fun sessionClock_tracksFocusAndBreaksAccurately() {
        val start = 1_000_000L
        val session = FocusSessionEntity(
            id = 1L,
            label = "Math",
            mode = FocusMode.TIMED,
            status = SessionStatus.ACTIVE,
            plannedDurationMinutes = 25,
            startedAt = start,
            breakMsCompleted = 60_000L,
            breakStartedAt = start + 600_000L,
            breakEndsAt = start + 900_000L
        )
        val duringBreak = SessionClock.computeSnapshot(session, start + 720_000L)
        assertTrue(duringBreak.isOnBreak)
        assertEquals(180_000L, duringBreak.breakElapsedMs)
        assertEquals(540_000L, duringBreak.focusElapsedMs)
        assertEquals(180_000L, duringBreak.breakRemainingMs)

        val afterBreak = SessionClock.computeSnapshot(session, start + 1_200_000L)
        assertFalse(afterBreak.isOnBreak)
        assertEquals(360_000L, afterBreak.breakElapsedMs)
        assertEquals(840_000L, afterBreak.focusElapsedMs)
    }

    @Test
    fun earnedBreaks_prefersLongBreakOnSharedMultiple() {
        val cfg = BreakConfigEntity(
            id = 1,
            enabled = true,
            tasksForShortBreak = 1,
            shortBreakMinutes = 5,
            tasksForLongBreak = 3,
            longBreakMinutes = 15
        )
        val snap = EarnedBreakCalculator.compute(completedTasks = 3, shortBreaksUsed = 1, longBreaksUsed = 0, config = cfg)
        assertEquals(2, snap.shortEarned)
        assertEquals(1, snap.longEarned)
        assertEquals(1, snap.shortAvailable)
        assertEquals(1, snap.longAvailable)
    }

    @Test
    fun blockPolicy_enforcesYoutubeAndAllowlistCorrectly() {
        val settings = AppSettings(blockYoutubeApp = true, blockYoutubeAppDuringBreaks = false, blockAllExceptAllowed = true)
        val blocked = listOf(BlockedAppEntity("com.instagram.android", "Instagram", AppCategory.SOCIAL, blockedDuringBreak = true))
        val allowed = listOf(AllowedAppEntity("com.android.chrome", "Chrome", AppCategory.CUSTOM))

        assertTrue(BlockPolicy.evaluateApp(BlockPolicy.YOUTUBE_PACKAGE, "YouTube", true, false, settings, blocked, allowed).shouldBlock)
        assertFalse(BlockPolicy.evaluateApp(BlockPolicy.YOUTUBE_PACKAGE, "YouTube", true, true, settings, blocked, allowed).shouldBlock)
        assertTrue(BlockPolicy.evaluateApp("com.instagram.android", "Instagram", true, true, settings, blocked, allowed).shouldBlock)
        assertFalse(BlockPolicy.evaluateApp("com.android.chrome", "Chrome", true, false, settings, blocked, allowed).shouldBlock)
        assertFalse(BlockPolicy.evaluateApp("com.android.dialer", "Dialer", true, false, settings, blocked, allowed).shouldBlock)
        assertTrue(BlockPolicy.shouldBlockYoutubeShorts(ShortsMode.ALWAYS, sessionActive = false, onBreak = false))
    }

    @Test
    fun youtubeClassifier_identifiesShortsHomeAndVideo() {
        val shorts = YouTubeUiClassifier.classify(YouTubeUiClassifier.parseUiNodeJson(YouTubeUiClassifier.SAMPLE_SHORTS_JSON))
        assertEquals(YouTubeScreenKind.SHORTS_PLAYER, shorts.kind)

        val home = YouTubeUiClassifier.classify(YouTubeUiClassifier.parseUiNodeJson(YouTubeUiClassifier.SAMPLE_HOME_JSON))
        assertEquals(YouTubeScreenKind.HOME_FEED, home.kind)

        val video = YouTubeUiClassifier.classify(YouTubeUiClassifier.parseUiNodeJson(YouTubeUiClassifier.SAMPLE_VIDEO_JSON))
        assertEquals(YouTubeScreenKind.REGULAR_YOUTUBE, video.kind)
    }

    @Test
    fun studyPlanParser_extractsSubjectsChaptersAndDurations() {
        val subjects = SeedData.BUILT_IN_SUBJECTS.mapIndexed { idx, name -> SubjectEntity(idx + 1L, name, true, idx) }
        val chapters = listOf(
            ChapterEntity(1L, 1L, "Complex Numbers and Quadratic Equations", 0),
            ChapterEntity(2L, 3L, "Thermodynamics", 0)
        )
        val plans = listOf(SubjectPlan(1L, "Mathematics", listOf(1L), "Complex Numbers and Quadratic Equations"))
        val tasks = StudyPlanParser.parseLocal(
            rawPlan = "I am doing quadratic equations, solving 20 questions for math and revising thermodynamics for chemistry",
            subjectPlans = plans,
            subjects = subjects,
            chapters = chapters
        )
        assertEquals(2, tasks.size)
        assertEquals(40, tasks[0].estimatedMinutes)
        assertEquals(1L, tasks[0].chapterId)
        assertEquals(3L, tasks[1].subjectId)
        assertEquals(20, tasks[1].estimatedMinutes)

        val implicitTasks = StudyPlanParser.parseLocal(
            rawPlan = "I am studying maths doing quadratic equations",
            subjectPlans = emptyList(),
            subjects = subjects,
            chapters = chapters
        )
        assertEquals(1, implicitTasks.size)
        assertFalse(StudyPlanParser.hasExplicitDuration("I am studying maths doing quadratic equations"))
        assertEquals(1L, implicitTasks[0].subjectId)
        assertEquals(1L, implicitTasks[0].chapterId)
        assertEquals(30, implicitTasks[0].estimatedMinutes)

        assertTrue(AiGrounding.numbersMatchFacts("You focused for 40 minutes and finished 2 tasks.", "Focus time: 40 minutes, Tasks completed: 2 of 2"))
        assertFalse(AiGrounding.numbersMatchFacts("You focused for 95 minutes today.", "Focus time: 40 minutes"))
    }
}
