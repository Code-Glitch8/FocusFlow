package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ActiveShieldAlert
import com.example.ui.theme.MonoMetricMedium

@Composable
fun FocusShieldDialog(
    alert: ActiveShieldAlert,
    sessionLabel: String?,
    onBreak: Boolean,
    clockLabel: String,
    onCloseBlockedApp: () -> Unit,
    onOpenRegularYoutube: () -> Unit,
    onReturnToFocusFlow: () -> Unit,
    onUnblockApp: () -> Unit
) {
    Dialog(
        onDismissRequest = onCloseBlockedApp,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    FocusFlowBrandMark(size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "FocusFlow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Focus Shield",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Blocked alert",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            alert.isYoutubeShorts -> "YOUTUBE SHORTS BLOCKED"
                            alert.isYoutubeApp -> "YOUTUBE BLOCKED"
                            else -> "${alert.appLabel.uppercase()} BLOCKED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "You should be focusing right now",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = alert.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (!sessionLabel.isNullOrBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (onBreak) "ON BREAK" else "ACTIVE FOCUS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (onBreak) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = sessionLabel,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = clockLabel,
                                style = MonoMetricMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onCloseBlockedApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("shield_close_blocked_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (alert.isYoutubeShorts) "Close YouTube Shorts" else "Close ${alert.appLabel}",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (alert.isYoutubeShorts) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenRegularYoutube,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("shield_open_regular_youtube_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Open regular YouTube instead")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onReturnToFocusFlow,
                        modifier = Modifier.testTag("shield_return_button")
                    ) {
                        Text("Return to FocusFlow")
                    }
                    if (!alert.isYoutubeShorts) {
                        TextButton(
                            onClick = onUnblockApp,
                            modifier = Modifier.testTag("shield_unblock_button")
                        ) {
                            Text("Unblock ${alert.appLabel}")
                        }
                    }
                }
            }
        }
    }
}
