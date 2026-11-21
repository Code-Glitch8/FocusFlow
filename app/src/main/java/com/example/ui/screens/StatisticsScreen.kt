package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.SessionClock
import com.example.model.ProgressRange
import com.example.ui.FocusFlowUiState
import com.example.ui.components.FocusFlowCard
import com.example.ui.components.MiniBarChart
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricSmall
import kotlin.math.max

@Composable
fun StatisticsScreen(
    state: FocusFlowUiState,
    onRateChapter: (chapterId: Long, stars: Int) -> Unit,
    onOpenHistory: () -> Unit
) {
    var range by remember { mutableStateOf(ProgressRange.DAYS) }
    val bars = when (range) {
        ProgressRange.DAYS -> state.stats.dailyMinutes
        ProgressRange.WEEKS -> state.stats.weeklyMinutes
        ProgressRange.MONTH -> state.stats.monthlyMinutes
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Statistics", style = MaterialTheme.typography.headlineLarge)
                OutlinedButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.testTag("stats_open_history_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("History")
                }
            }
        }

        item {
            FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Lifetime overview")
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatColumn(
                        value = SessionClock.formatMinutesCompact(state.stats.totalFocusMinutes),
                        label = "total focused",
                        modifier = Modifier.weight(1f)
                    )
                    StatColumn(
                        value = SessionClock.formatMinutesCompact(state.stats.last7DaysMinutes),
                        label = "last 7 days",
                        modifier = Modifier.weight(1f)
                    )
                    StatColumn(
                        value = "${state.stats.streakDays}d",
                        label = "streak",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatColumn(
                        value = state.stats.totalSessions.toString(),
                        label = "sessions",
                        modifier = Modifier.weight(1f)
                    )
                    StatColumn(
                        value = state.stats.totalCompletedTasks.toString(),
                        label = "tasks done",
                        modifier = Modifier.weight(1f)
                    )
                    StatColumn(
                        value = state.stats.totalOpenTasks.toString(),
                        label = "tasks open",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Focus activity")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressRange.entries.forEach { r ->
                        FilterChip(
                            selected = range == r,
                            onClick = { range = r },
                            label = { Text(r.label) },
                            modifier = Modifier.testTag("stats_range_${r.name.lowercase()}")
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                MiniBarChart(
                    bars = bars,
                    height = 132.dp,
                    barColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Time by subject")
                if (state.stats.subjectBreakdown.isEmpty()) {
                    Text(
                        text = "Complete a subject-linked session to see your subject breakdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val maxMins = max(1, state.stats.subjectBreakdown.maxOf { it.second })
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.stats.subjectBreakdown.forEach { (subject, mins) ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(subject, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        SessionClock.formatMinutesCompact(mins),
                                        style = MonoMetricSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (mins.toFloat() / maxMins.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.stats.allChapterProgress.isNotEmpty()) {
            item {
                SectionHeader("Chapters & self-confidence", modifier = Modifier.padding(top = 4.dp))
            }
            items(state.stats.allChapterProgress, key = { "stat_ch_${it.chapterId}" }) { ch ->
                val rating = state.settings.chapterConfidence[ch.chapterId] ?: 0
                FocusFlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ch.chapterName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${ch.subjectName} · ${SessionClock.formatMinutesCompact(ch.totalMinutes)} total · ${ch.doneTasks} done / ${ch.openTasks} open",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (rating == 0) "Self-confidence: unrated" else "Confidence: $rating/5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            (1..5).forEach { star ->
                                IconButton(
                                    onClick = { onRateChapter(ch.chapterId, star) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("chapter_confidence_${ch.chapterId}_$star")
                                ) {
                                    Icon(
                                        imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "Rate $star stars",
                                        tint = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MonoMetricLarge)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
