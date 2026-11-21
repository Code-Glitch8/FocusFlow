package com.example.domain

import com.example.model.UiNode
import com.example.model.YouTubeDetectionResult
import com.example.model.YouTubeScreenKind
import org.json.JSONArray
import org.json.JSONObject

object YouTubeUiClassifier {

    private val SHORTS_VIEW_ID_PREFIXES = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_player_overlay",
        "reel_watch_fragment_root",
        "reel_time_bar",
        "reel_progress_bar",
        "shorts_player"
    )

    fun classify(root: UiNode): YouTubeDetectionResult {
        val sampledIds = linkedSetOf<String>()
        var matchedShortsId: String? = null
        var shortsTabSelected = false
        var homeTabSelected = false

        val queue = ArrayDeque<UiNode>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val rawId = node.viewId?.trim().orEmpty()
            val shortId = rawId.substringAfter(":id/", rawId).lowercase()
            if (shortId.isNotEmpty() && sampledIds.size < 40) {
                sampledIds.add(shortId)
            }

            if (matchedShortsId == null && shortId.isNotEmpty()) {
                val match = SHORTS_VIEW_ID_PREFIXES.firstOrNull { prefix ->
                    shortId == prefix || shortId.startsWith(prefix)
                }
                if (match != null) {
                    matchedShortsId = shortId
                }
            }

            val label = (node.contentDescription ?: node.text)?.trim()?.lowercase().orEmpty()
            if (node.selected) {
                when (label) {
                    "shorts" -> shortsTabSelected = true
                    "home" -> homeTabSelected = true
                }
            }

            for (child in node.children) {
                queue.addLast(child)
            }
        }

        val sampledList = sampledIds.toList()
        return when {
            matchedShortsId != null -> YouTubeDetectionResult(
                kind = YouTubeScreenKind.SHORTS_PLAYER,
                reason = "Matched Shorts view ID '$matchedShortsId'",
                matchedId = matchedShortsId,
                sampledViewIds = sampledList
            )
            shortsTabSelected -> YouTubeDetectionResult(
                kind = YouTubeScreenKind.SHORTS_PLAYER,
                reason = "Selected tab label is 'Shorts'",
                matchedId = "tab:shorts",
                sampledViewIds = sampledList
            )
            homeTabSelected -> YouTubeDetectionResult(
                kind = YouTubeScreenKind.HOME_FEED,
                reason = "Selected tab label is 'Home'",
                matchedId = "tab:home",
                sampledViewIds = sampledList
            )
            else -> YouTubeDetectionResult(
                kind = YouTubeScreenKind.REGULAR_YOUTUBE,
                reason = "No Shorts view IDs or Shorts/Home selected tabs detected",
                matchedId = null,
                sampledViewIds = sampledList
            )
        }
    }

    fun parseUiNodeJson(jsonString: String): UiNode {
        val obj = JSONObject(jsonString.trim())
        return parseJsonObject(obj)
    }

    private fun parseJsonObject(obj: JSONObject): UiNode {
        val viewId = obj.optString("viewId", "").ifBlank { null }
        val desc = obj.optString("contentDescription", "").ifBlank { null }
        val text = obj.optString("text", "").ifBlank { null }
        val selected = obj.optBoolean("selected", false)
        val childrenArr = obj.optJSONArray("children") ?: JSONArray()
        val children = buildList {
            for (i in 0 until childrenArr.length()) {
                val childObj = childrenArr.optJSONObject(i) ?: continue
                add(parseJsonObject(childObj))
            }
        }
        return UiNode(
            viewId = viewId,
            contentDescription = desc,
            text = text,
            selected = selected,
            children = children
        )
    }

    val SAMPLE_SHORTS_JSON = """
        {
          "viewId": "com.google.android.youtube:id/coordinator",
          "children": [
            { "viewId": "com.google.android.youtube:id/reel_player_page_container" },
            { "viewId": "com.google.android.youtube:id/reel_player_overlay" },
            { "contentDescription": "Shorts", "selected": true }
          ]
        }
    """.trimIndent()

    val SAMPLE_HOME_JSON = """
        {
          "viewId": "com.google.android.youtube:id/results",
          "children": [
            { "viewId": "com.google.android.youtube:id/pivot_bar", "children": [
              { "contentDescription": "Home", "selected": true },
              { "contentDescription": "Subscriptions", "selected": false }
            ] }
          ]
        }
    """.trimIndent()

    val SAMPLE_VIDEO_JSON = """
        {
          "viewId": "com.google.android.youtube:id/watch_player",
          "children": [
            { "viewId": "com.google.android.youtube:id/player_view" },
            { "contentDescription": "Subscriptions", "selected": true }
          ]
        }
    """.trimIndent()
}
