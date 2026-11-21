package com.example.domain

import com.example.data.local.BreakConfigEntity
import com.example.model.EarnedBreakSnapshot
import kotlin.math.max

object EarnedBreakCalculator {

    fun compute(
        completedTasks: Int,
        shortBreaksUsed: Int,
        longBreaksUsed: Int,
        config: BreakConfigEntity
    ): EarnedBreakSnapshot {
        if (!config.enabled) {
            return EarnedBreakSnapshot(
                shortEarned = 0,
                longEarned = 0,
                shortAvailable = 0,
                longAvailable = 0,
                nextShortInTasks = null,
                nextLongInTasks = null
            )
        }

        var shortEarned = 0
        var longEarned = 0
        val safeTasks = max(0, completedTasks)
        val shortFreq = config.tasksForShortBreak
        val longFreq = config.tasksForLongBreak

        for (c in 1..safeTasks) {
            when {
                longFreq > 0 && c % longFreq == 0 -> longEarned++
                shortFreq > 0 && c % shortFreq == 0 -> shortEarned++
            }
        }

        val shortAvailable = max(0, shortEarned - shortBreaksUsed)
        val longAvailable = max(0, longEarned - longBreaksUsed)

        val nextLongIn = if (longFreq > 0) {
            val rem = safeTasks % longFreq
            if (rem == 0) longFreq else longFreq - rem
        } else null

        val nextShortIn = if (shortFreq > 0) {
            var step = 1
            while (step <= shortFreq) {
                val target = safeTasks + step
                if ((longFreq <= 0 || target % longFreq != 0) && target % shortFreq == 0) {
                    break
                }
                step++
            }
            if (step <= shortFreq) step else null
        } else null

        return EarnedBreakSnapshot(
            shortEarned = shortEarned,
            longEarned = longEarned,
            shortAvailable = shortAvailable,
            longAvailable = longAvailable,
            nextShortInTasks = nextShortIn,
            nextLongInTasks = nextLongIn
        )
    }
}
