package com.zentryx.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentryx.music.lyrics.LyricsParser
import com.zentryx.music.lyrics.ParsedLyrics
import com.zentryx.music.ui.theme.MbuxSurface
import com.zentryx.music.ui.theme.ZentryxLime

/**
 * Feature 5: Cockpit Synchronized Karaoke & Lyrics View.
 * Displays smooth auto-scrolling lyrics with glowing MBUX electric lime highlight.
 */
@Composable
fun LyricsView(
    lyrics: ParsedLyrics,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = LyricsParser.findActiveLineIndex(lyrics.lines, currentPositionMs)

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MbuxSurface)
            .padding(12.dp)
    ) {
        if (lyrics.lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No synchronized LRC lyrics available for this track",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 40.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = index == activeIndex
                    val textColor = if (isActive) ZentryxLime else Color(0xFF94A3B8)
                    val fontSize = if (isActive) 18.sp else 14.sp
                    val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                    Text(
                        text = line.text.ifBlank { "•••" },
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                onSeekTo(line.timestampMs)
                            }
                    )
                }
            }
        }
    }
}
