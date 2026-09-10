package com.profecuaderno.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp

@Composable
fun NotebookBackground(
    style: AgendaThemeStyle,
    content: @Composable () -> Unit
) {
    val paper = agendaPaperColor(style)
    val rule = agendaRuleColor(style)
    Box(
        Modifier
            .fillMaxSize()
            .background(paper)
            .drawBehind {
                val gap = 32.dp.toPx()
                var y = gap
                while (y < size.height) {
                    drawLine(
                        color = rule,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 0.8f
                    )
                    y += gap
                }
            }
    ) { content() }
}
