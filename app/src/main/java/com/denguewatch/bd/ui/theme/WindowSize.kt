package com.denguewatch.bd.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The space the app's window actually has, in dp.
 *
 * Not the screen's: in split screen or a freeform window the app gets part of
 * the display, and a layout chosen from the whole screen — the side rail, the
 * stat grid's width, the map's side-by-side panel — would be laid out for room
 * it does not have.
 */
@Composable
fun windowSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

/** Below this window height the layout is short: a phone on its side. */
val ShortHeightBreakpoint = 480.dp
