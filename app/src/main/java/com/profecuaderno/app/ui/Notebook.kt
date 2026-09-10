package com.profecuaderno.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp

@Composable
fun NotebookBackground(style: AgendaThemeStyle, content: @Composable () -> Unit) {
    val paper = MaterialTheme.colorScheme.background
    val rule = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Box(Modifier.fillMaxSize().background(paper).drawBehind {
        val gap = 32.dp.toPx(); var y = gap
        while (y < size.height) { drawLine(rule, androidx.compose.ui.geometry.Offset(0f,y), androidx.compose.ui.geometry.Offset(size.width,y), 0.8f); y += gap }
    }) { content() }
}
