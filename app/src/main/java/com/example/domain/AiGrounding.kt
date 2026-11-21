package com.example.domain

import com.example.data.local.ChapterEntity
import com.example.data.local.SubjectEntity
import com.example.model.DraftStudyTask
import com.example.model.SubjectPlan

object AiGrounding {

    private val NUMBER_REGEX = Regex("""\b\d+(?:\.\d+)?\b""")

    fun keepGrounded(
        candidates: List<DraftStudyTask>,
        studentPlanText: String,
        subjectPlans: List<SubjectPlan>,
        subjects: List<SubjectEntity>,
        chapters: List<ChapterEntity>
    ): List<DraftStudyTask> {
        val referenceBlob = buildString {
            append(studentPlanText.lowercase()).append(' ')
            subjectPlans.forEach {
                append(it.subjectName.lowercase()).append(' ')
                append(it.chaptersSummary.lowercase()).append(' ')
            }
            subjects.forEach { append(it.name.lowercase()).append(' ') }
            chapters.forEach { append(it.name.lowercase()).append(' ') }
        }
        val refStems = referenceBlob
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .map { it.take(4) }
            .toSet()

        val deduped = linkedMapOf<String, DraftStudyTask>()
        for (task in candidates) {
            val titleTokens = task.title.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 }
            val grounded = titleTokens.isEmpty() || titleTokens.any { it.take(4) in refStems }
            if (!grounded) continue

            val clampedMinutes = (((task.estimatedMinutes.coerceIn(10, 240)) + 2) / 5) * 5
            val key = "${task.chapterId ?: 0}|${task.title.trim().lowercase().trimEnd('.', ',', ';')}"
            if (!deduped.containsKey(key)) {
                deduped[key] = task.copy(
                    estimatedMinutes = clampedMinutes,
                    dedupeKey = key
                )
            }
        }
        return deduped.values.toList()
    }

    fun numbersMatchFacts(summary: String, factsText: String): Boolean {
        val factNumbers = NUMBER_REGEX.findAll(factsText).map { it.value }.toSet()
        val summaryNumbers = NUMBER_REGEX.findAll(summary).map { it.value }.toList()
        if (summaryNumbers.isEmpty()) return true
        // Allow small generic counts (1, 2, 3) if needed, or ensure all larger numbers exist in facts
        return summaryNumbers.all { num ->
            num in factNumbers || num in setOf("1", "2", "3", "4", "5")
        }
    }
}
