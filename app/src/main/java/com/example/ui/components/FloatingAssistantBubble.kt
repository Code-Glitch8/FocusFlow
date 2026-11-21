package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.FocusSessionEntity
import com.example.data.local.SessionTaskEntity
import com.example.domain.SessionClock
import com.example.model.EarnedBreakSnapshot
import com.example.model.SessionTimeSnapshot
import com.example.ui.theme.MonoMetricSmall
import kotlin.math.roundToInt

@Composable
fun FloatingAssistantBubble(
    session: FocusSessionEntity,
    time: SessionTimeSnapshot,
    tasks: List<SessionTaskEntity>,
    earnedBreaks: EarnedBreakSnapshot?,
    onToggleTask: (SessionTaskEntity) -> Unit,
    onStartShortBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onOpenFocusScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val clockText = when {
        time.isOnBreak -> SessionClock.formatClock(time.breakRemainingMs)
        time.remainingMs != null -> SessionClock.formatClock(time.remainingMs)
        else -> SessionClock.formatClock(time.focusElapsedMs)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 84.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        if (!expanded) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(62.dp)
                    .testTag("floating_assistant_bubble")
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .clickable { expanded = true },
                shape = CircleShape,
                color = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = clockText,
                        style = MonoMetricSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (time.isOnBreak) MaterialTheme.colorScheme.onSecondary
                        else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .testTag("floating_assistant_card"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (time.isOnBreak) "ON BREAK · $clockText" else "FOCUSING · $clockText",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (time.isOnBreak) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = session.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { expanded = false },
                            modifier = Modifier.testTag("floating_assistant_collapse")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Collapse assistant")
                        }
                    }

                    if (tasks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        tasks.take(4).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleTask(task) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (task.completed) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = if (task.completed) "Mark undone" else "Mark done",
                                    tint = if (task.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (time.isOnBreak) {
                            OutlinedButton(
                                onClick = { onEndBreak(); expanded = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("End break")
                            }
                        } else if ((earnedBreaks?.shortAvailable ?: 0) > 0) {
                            OutlinedButton(
                                onClick = { onStartShortBreak(); expanded = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Take break")
                            }
                        }
                        Button(
                            onClick = { expanded = false; onOpenFocusScreen() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open Focus")
                        }
                    }
                }
            }
        }
    }
}
