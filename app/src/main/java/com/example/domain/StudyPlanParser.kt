package com.example.domain

import com.example.data.local.ChapterEntity
import com.example.data.local.SubjectEntity
import com.example.model.DraftStudyTask
import com.example.model.SubjectPlan
import com.example.model.SubtaskDraft
import com.example.model.TaskOrigin
import java.util.UUID
import kotlin.math.roundToInt

object StudyPlanParser {

    private val SUBJECT_KEYWORDS = mapOf(
        "mathematics" to listOf("math", "maths", "mathematics", "algebra", "calculus", "geometry", "trigonometr", "probability", "binomial", "quadratic", "inequalit", "conic", "permutation", "sequence", "limit"),
        "physics" to listOf("physics", "kinematics", "gravitation", "optics", "mechanics", "rotational", "waves", "oscillation", "kinetic", "fluid", "motion", "newton", "work, energy"),
        "chemistry" to listOf("chemistry", "chem", "organic", "inorganic", "equilibrium", "redox", "hydrocarbon", "mole", "atom", "periodic", "bonding", "enthalpy"),
        "biology" to listOf("biology", "bio", "cell", "genetics", "anatomy", "botany", "zoology", "photosynthesis", "respiration", "enzyme"),
        "computer science" to listOf("coding", "code", "programming", "python", "kotlin", "java", "algorithm", "sql"),
        "english" to listOf("english", "essay", "grammar", "comprehension", "literature", "poem"),
        "history" to listOf("history", "civilization", "revolution", "empire", "constitution")
    )

    private val HOURS_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:hours?|hrs?|hr)\b""", RegexOption.IGNORE_CASE)
    private val MINS_REGEX = Regex("""(\d+)\s*(?:minutes?|mins?|min)\b""", RegexOption.IGNORE_CASE)
    private val QUESTIONS_REGEX = Regex("""(\d+)\s*(?:questions?|qs?|problems?|mcqs?|numericals?|sums?|exercises?)\b""", RegexOption.IGNORE_CASE)
    private val PAGES_REGEX = Regex("""(\d+)\s*(?:pages?|pgs?)\b""", RegexOption.IGNORE_CASE)
    private val SPLIT_REGEX = Regex("""\s*(?:\n+|;|(?<=.)\.\s+|\band\s+(?=(?:i\s+am|i'm|also|then|do(?:ing)?|solv(?:e|ing)|revis(?:e|ing)|review(?:ing)?|read(?:ing)?|practic(?:e|ing)|study(?:ing)?|finish(?:ing)?|complet(?:e|ing)|work(?:ing)?)\b)|\bthen\s+)\s*""", RegexOption.IGNORE_CASE)
    private val CLEAN_PREFIX_REGEX = Regex("""^(?:i\s+am\s+|i'm\s+|i\s+need\s+to\s+|need\s+to\s+|want\s+to\s+|going\s+to\s+|also\s+|then\s+|and\s+)+""", RegexOption.IGNORE_CASE)

    fun parseLocal(
        rawPlan: String,
        subjectPlans: List<SubjectPlan>,
        subjects: List<SubjectEntity>,
        chapters: List<ChapterEntity>,
        origin: TaskOrigin = TaskOrigin.AI_TEXT
    ): List<DraftStudyTask> {
        val trimmed = rawPlan.trim()
        if (trimmed.isEmpty()) return emptyList()

        val clauses = trimmed.split(SPLIT_REGEX)
            .map { it.trim().trimEnd('.', ',', ';') }
            .filter { it.length >= 3 }
            .ifEmpty { listOf(trimmed) }

        val seenKeys = hashSetOf<String>()
        val results = mutableListOf<DraftStudyTask>()

        for (clause in clauses) {
            val cleaned = cleanTitle(clause)
            if (cleaned.length < 3) continue

            val matchedSubject = matchSubject(clause, subjectPlans, subjects)
            val matchedChapter = matchChapter(clause, matchedSubject?.id, subjectPlans, chapters)
            val effectiveSubjectId = matchedChapter?.subjectId ?: matchedSubject?.id
                ?: subjectPlans.firstOrNull()?.subjectId

            val minutes = estimateMinutes(clause)
            val dedupeKey = "${matchedChapter?.id ?: 0}|${cleaned.lowercase()}"
            if (!seenKeys.add(dedupeKey)) continue

            val subtasks = buildSubtasksIfLong(cleaned, minutes)

            results.add(
                DraftStudyTask(
                    id = UUID.randomUUID().toString(),
                    title = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    estimatedMinutes = minutes,
                    subjectId = effectiveSubjectId,
                    chapterId = matchedChapter?.id,
                    subtasks = subtasks,
                    origin = origin,
                    dedupeKey = dedupeKey
                )
            )
        }

        return results
    }

    fun hasExplicitDuration(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("half an hour") ||
            lower.contains("half hour") ||
            HOURS_REGEX.containsMatchIn(lower) ||
            MINS_REGEX.containsMatchIn(lower)
    }

    fun estimateMinutes(text: String): Int {
        val lower = text.lowercase()
        var minutes = when {
            lower.contains("half an hour") || lower.contains("half hour") -> 30
            HOURS_REGEX.containsMatchIn(lower) -> {
                val hrs = HOURS_REGEX.find(lower)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                (hrs * 60.0).roundToInt()
            }
            MINS_REGEX.containsMatchIn(lower) -> {
                MINS_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 25
            }
            QUESTIONS_REGEX.containsMatchIn(lower) -> {
                val count = QUESTIONS_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 10
                count * 2
            }
            PAGES_REGEX.containsMatchIn(lower) -> {
                val pages = PAGES_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 8
                pages * 3
            }
            listOf("mock test", "exam", "full paper", "test paper", "sample paper").any { lower.contains(it) } -> 60
            listOf("revise", "revising", "revision", "review", "read", "notes", "summary", "flashcards").any { lower.contains(it) } -> 20
            listOf("equation", "calculus", "derivation", "numerical", "coding", "project", "problem set").any { lower.contains(it) } -> 30
            listOf("solve", "solving", "practice", "practicing", "homework", "assignment", "worksheet", "doing").any { lower.contains(it) } -> 25
            else -> 25
        }
        minutes = minutes.coerceIn(10, 240)
        return ((minutes + 2) / 5) * 5
    }

    private fun cleanTitle(raw: String): String {
        var current = raw.trim()
        while (true) {
            val next = current.replace(CLEAN_PREFIX_REGEX, "").trim()
            if (next == current) break
            current = next
        }
        return current.take(120)
    }

    private fun matchSubject(
        clause: String,
        subjectPlans: List<SubjectPlan>,
        subjects: List<SubjectEntity>
    ): SubjectEntity? {
        val lower = clause.lowercase()
        // Direct match against selected subjects first
        for (plan in subjectPlans) {
            val subj = subjects.find { it.id == plan.subjectId } ?: continue
            if (lower.contains(subj.name.lowercase())) return subj
        }
        // Keyword map match
        for ((canonName, keywords) in SUBJECT_KEYWORDS) {
            if (keywords.any { lower.contains(it) }) {
                val exact = subjects.find { it.name.equals(canonName, ignoreCase = true) }
                if (exact != null) return exact
            }
        }
        // Direct match against all subjects
        return subjects.firstOrNull { lower.contains(it.name.lowercase()) }
    }

    private fun matchChapter(
        clause: String,
        preferredSubjectId: Long?,
        subjectPlans: List<SubjectPlan>,
        chapters: List<ChapterEntity>
    ): ChapterEntity? {
        val tokens = tokenize(clause)
        if (tokens.isEmpty()) return null

        val candidateChapters = when {
            preferredSubjectId != null -> chapters.filter { it.subjectId == preferredSubjectId }
            subjectPlans.isNotEmpty() -> {
                val ids = subjectPlans.flatMap { it.chapterIds }.toSet()
                if (ids.isNotEmpty()) chapters.filter { it.id in ids } else chapters
            }
            else -> chapters
        }

        var best: ChapterEntity? = null
        var bestScore = 0
        for (chapter in candidateChapters) {
            val chapterTokens = tokenize(chapter.name)
            if (chapterTokens.isEmpty()) continue
            var score = 0
            for (ct in chapterTokens) {
                val ctStem = ct.take(5)
                if (tokens.any { it == ct || (it.length >= 4 && ct.length >= 4 && it.take(4) == ct.take(4)) || (ctStem.length >= 4 && it.startsWith(ctStem)) }) {
                    score += if (ct.length >= 6) 2 else 1
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = chapter
            }
        }
        return if (bestScore > 0) best else null
    }

    private fun tokenize(text: String): List<String> {
        val stopWords = setOf("and", "the", "for", "with", "into", "from", "some", "basic", "principles", "chapter", "study", "revise", "solve")
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in stopWords }
    }

    private fun buildSubtasksIfLong(title: String, minutes: Int): List<SubtaskDraft> {
        if (minutes < 45) return emptyList()
        return listOf(
            SubtaskDraft(UUID.randomUUID().toString(), "Review core concepts for $title"),
            SubtaskDraft(UUID.randomUUID().toString(), "Complete practice problems & verify")
        )
    }
}
