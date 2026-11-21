package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.local.AllowedAppEntity
import com.example.data.local.BlockedAppEntity
import com.example.domain.BlockPolicy
import com.example.domain.SessionClock
import com.example.model.AppCategory
import com.example.model.AppSettings
import com.example.model.WidgetIcon
import com.example.ui.theme.MonoMetricSmall
import kotlin.math.max

@Composable
fun FocusFlowBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = "FocusFlow logo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
fun FocusFlowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun CatalogAppIcon(
    packageName: String,
    label: String,
    category: AppCategory,
    isBlocked: Boolean = false,
    isAllowed: Boolean = false,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedBitmap: ImageBitmap? = remember(packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(96, 96)
                .asImageBitmap()
        }.getOrNull()
    }
    val (fallbackIcon, badgeTint) = resolveCatalogIconStyle(packageName, category)

    Box(modifier = modifier.size(size)) {
        if (installedBitmap != null) {
            Image(
                bitmap = installedBitmap,
                contentDescription = "$label icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = when {
                            isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                            isAllowed -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeTint.copy(alpha = 0.16f))
                    .border(
                        width = 1.dp,
                        color = when {
                            isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                            isAllowed -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                            else -> badgeTint.copy(alpha = 0.35f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = "$label icon",
                    tint = badgeTint,
                    modifier = Modifier.size(size * 0.54f)
                )
            }
        }

        if (isBlocked || isAllowed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (isBlocked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary
                    )
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Filled.Block else Icons.Filled.Check,
                    contentDescription = if (isBlocked) "Blocked" else "Allowed",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

private fun resolveCatalogIconStyle(packageName: String, category: AppCategory): Pair<ImageVector, Color> {
    val pkg = packageName.lowercase()
    return when {
        pkg == BlockPolicy.YOUTUBE_PACKAGE -> Icons.Filled.SmartDisplay to Color(0xFFFF5252)
        pkg.contains("youtube.music") -> Icons.Filled.Headphones to Color(0xFFFF6D6D)
        pkg.contains("spotify") -> Icons.Filled.Headphones to Color(0xFF1DB954)
        pkg.contains("instagram") && !pkg.contains("barcelona") -> Icons.Filled.PhotoCamera to Color(0xFFE1306C)
        pkg.contains("musically") -> Icons.Filled.Movie to Color(0xFF00E5FF)
        pkg.contains("snapchat") -> Icons.Filled.Chat to Color(0xFFFFC400)
        pkg.contains("twitter") -> Icons.Filled.Public to Color(0xFF4FC3F7)
        pkg.contains("reddit") -> Icons.Filled.Forum to Color(0xFFFF5722)
        pkg.contains("discord") -> Icons.Filled.Forum to Color(0xFF7289DA)
        pkg.contains("facebook") -> Icons.Filled.Groups to Color(0xFF4267B2)
        pkg.contains("barcelona") -> Icons.Filled.Public to Color(0xFFB0BEC5)
        pkg.contains("pinterest") -> Icons.Filled.Bookmark to Color(0xFFE60023)
        pkg.contains("netflix") -> Icons.Filled.Movie to Color(0xFFE50914)
        pkg.contains("twitch") -> Icons.Filled.LiveTv to Color(0xFF9146FF)
        pkg.contains("disney") -> Icons.Filled.LiveTv to Color(0xFF113CCF)
        pkg.contains("whatsapp") -> Icons.Filled.Chat to Color(0xFF25D366)
        pkg.contains("telegram") -> Icons.Filled.Chat to Color(0xFF29B6F6)
        pkg.contains("chrome") -> Icons.Filled.Language to Color(0xFF6EA8FF)
        pkg.contains(".gm") || pkg.contains("mail") -> Icons.Filled.Email to Color(0xFFEA4335)
        pkg.contains("docs") -> Icons.Filled.Description to Color(0xFF4285F4)
        pkg.contains("snapgpt") -> Icons.Filled.AutoAwesome to Color(0xFF7FD6C2)
        else -> when (category) {
            AppCategory.SOCIAL -> Icons.Filled.Groups to Color(0xFFE06C9F)
            AppCategory.ENTERTAINMENT -> Icons.Filled.LiveTv to Color(0xFFFF8A65)
            AppCategory.GAME -> Icons.Filled.SportsEsports to Color(0xFF9575CD)
            AppCategory.CUSTOM -> Icons.Filled.Apps to Color(0xFF6EA8FF)
        }
    }
}

fun widgetIconToVector(icon: WidgetIcon): ImageVector = when (icon) {
    WidgetIcon.TIMER -> Icons.Filled.Timer
    WidgetIcon.PLAY -> Icons.Filled.PlayArrow
    WidgetIcon.BOOK -> Icons.Filled.MenuBook
    WidgetIcon.CALCULATE -> Icons.Filled.Calculate
    WidgetIcon.SCIENCE -> Icons.Filled.Science
    WidgetIcon.BIOTECH -> Icons.Filled.Biotech
    WidgetIcon.BOLT -> Icons.Filled.Bolt
    WidgetIcon.EDIT -> Icons.Filled.Edit
    WidgetIcon.STAR -> Icons.Filled.Star
    WidgetIcon.SCHOOL -> Icons.Filled.School
    WidgetIcon.ALARM -> Icons.Filled.Alarm
    WidgetIcon.BOOKMARK -> Icons.Filled.Bookmark
    WidgetIcon.PSYCHOLOGY -> Icons.Filled.Psychology
    WidgetIcon.CODE -> Icons.Filled.Code
    WidgetIcon.LANGUAGE -> Icons.Filled.Language
    WidgetIcon.TROPHY -> Icons.Filled.EmojiEvents
}

@Composable
fun MiniBarChart(
    bars: List<Pair<String, Int>>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 112.dp,
    modifier: Modifier = Modifier
) {
    val maxVal = max(1, bars.maxOfOrNull { it.second } ?: 1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { (label, mins) ->
            val ratio = (mins.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (mins > 0) {
                    Text(
                        text = SessionClock.formatMinutesCompact(mins),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                } else {
                    Spacer(Modifier.height(18.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (mins > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((height * max(0.08f, ratio)))
                                .clip(RoundedCornerShape(8.dp))
                                .background(barColor)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AppGuardBar(
    sessionActive: Boolean,
    onBreak: Boolean,
    settings: AppSettings,
    blockedApps: List<BlockedAppEntity>,
    allowedApps: List<AllowedAppEntity>,
    statusBanner: String?,
    onDismissBanner: () -> Unit,
    onTestTarget: (packageName: String, label: String, isShorts: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val ytDecision = BlockPolicy.evaluateApp(
        BlockPolicy.YOUTUBE_PACKAGE,
        "YouTube",
        sessionActive,
        onBreak,
        settings,
        blockedApps,
        allowedApps
    )
    val shortsBlocked = BlockPolicy.shouldBlockYoutubeShorts(
        settings.blockYoutubeShorts,
        sessionActive,
        onBreak
    ) || ytDecision.shouldBlock

    val instaDecision = BlockPolicy.evaluateApp(
        "com.instagram.android",
        "Instagram",
        sessionActive,
        onBreak,
        settings,
        blockedApps,
        allowedApps
    )
    val tiktokDecision = BlockPolicy.evaluateApp(
        "com.zhiliaoapp.musically",
        "TikTok",
        sessionActive,
        onBreak,
        settings,
        blockedApps,
        allowedApps
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val targets = listOf(
            GuardTileData(
                tag = "guard_open_youtube",
                packageName = BlockPolicy.YOUTUBE_PACKAGE,
                title = "YouTube",
                category = AppCategory.ENTERTAINMENT,
                isShorts = false,
                blocked = ytDecision.shouldBlock,
                statusText = if (ytDecision.shouldBlock) "Blocked now" else "Allowed now",
                onClick = { onTestTarget(BlockPolicy.YOUTUBE_PACKAGE, "YouTube", false) }
            ),
            GuardTileData(
                tag = "guard_open_shorts",
                packageName = BlockPolicy.YOUTUBE_PACKAGE,
                title = "YT Shorts",
                category = AppCategory.ENTERTAINMENT,
                isShorts = true,
                blocked = shortsBlocked,
                statusText = if (shortsBlocked) "Blocked now" else "Allowed now",
                onClick = { onTestTarget(BlockPolicy.YOUTUBE_PACKAGE, "YouTube Shorts", true) }
            ),
            GuardTileData(
                tag = "guard_open_instagram",
                packageName = "com.instagram.android",
                title = "Instagram",
                category = AppCategory.SOCIAL,
                isShorts = false,
                blocked = instaDecision.shouldBlock,
                statusText = if (instaDecision.shouldBlock) "Blocked now" else "Allowed now",
                onClick = { onTestTarget("com.instagram.android", "Instagram", false) }
            ),
            GuardTileData(
                tag = "guard_open_tiktok",
                packageName = "com.zhiliaoapp.musically",
                title = "TikTok",
                category = AppCategory.SOCIAL,
                isShorts = false,
                blocked = tiktokDecision.shouldBlock,
                statusText = if (tiktokDecision.shouldBlock) "Blocked now" else "Allowed now",
                onClick = { onTestTarget("com.zhiliaoapp.musically", "TikTok", false) }
            )
        )

        targets.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { tile ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(tile.tag)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = tile.onClick),
                        shape = RoundedCornerShape(14.dp),
                        color = if (tile.blocked) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (tile.blocked) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tile.isShorts) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFF5252).copy(alpha = 0.16f))
                                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircleOutline,
                                        contentDescription = "YouTube Shorts",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                CatalogAppIcon(
                                    packageName = tile.packageName,
                                    label = tile.title,
                                    category = tile.category,
                                    isBlocked = tile.blocked,
                                    size = 38.dp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tile.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tile.statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (tile.blocked) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Launch ${tile.title}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!statusBanner.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusBanner,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = onDismissBanner,
                        modifier = Modifier.testTag("dismiss_guard_banner")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss status banner",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class GuardTileData(
    val tag: String,
    val packageName: String,
    val title: String,
    val category: AppCategory,
    val isShorts: Boolean,
    val blocked: Boolean,
    val statusText: String,
    val onClick: () -> Unit
)

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    FocusFlowCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = badge,
                        style = MonoMetricSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
