package com.profecuaderno.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ThemeSelectionScreen(
    initial: AgendaThemeStyle = AgendaThemeStyle.MINT_LAVENDER,
    onSelected: (AgendaThemeStyle) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selected by remember(initial) { mutableStateOf(initial) }
    val language = LocalAppLanguage.current

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Palette, null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(language.text("Hazla tuya", "Make it yours"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(language.text("Elige cómo quieres ver tu agenda. Podrás cambiarlo cuando quieras.", "Choose how you want your planner to look. You can change it anytime."))
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(language.text("Idioma / Language", "Language / Idioma"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { item ->
                        FilterChip(
                            selected = language == item,
                            onClick = {
                                if (language != item) {
                                    AppLanguagePrefs.save(context, item)
                                    (context as? Activity)?.recreate()
                                }
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        AgendaThemeStyle.entries.forEach { style ->
            ThemePreviewCard(style = style, selected = style == selected, onClick = { selected = style })
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onCancel != null) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(language.text("Cancelar", "Cancel")) }
            }
            Button(
                onClick = { onSelected(selected) },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text(language.text("Usar este estilo", "Use this style"), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ThemePreviewCard(style: AgendaThemeStyle, selected: Boolean, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    val colors = when (style) {
        AgendaThemeStyle.GRAPHITE_BLUE -> listOf(Color(0xFF1D2733), Color(0xFF234A73), Color(0xFF9CA7B2))
        AgendaThemeStyle.SUNSET_GARDEN -> listOf(Color(0xFFD39A16), Color(0xFFB93A32), Color(0xFF3F7B4E))
        AgendaThemeStyle.BOLD_CLASSIC -> listOf(Color(0xFFB52F3A), Color(0xFF245C9A), Color(0xFFD65B2B))
        AgendaThemeStyle.MINT_LAVENDER -> listOf(Color(0xFF7654A8), Color(0xFF50BDB3), Color(0xFF319DA5))
        AgendaThemeStyle.PINK_BLUE -> listOf(Color(0xFFC44F82), Color(0xFF4777B8), Color(0xFF7B68B5))
        AgendaThemeStyle.GRAYSCALE -> listOf(Color(0xFF202124), Color(0xFF666A70), Color(0xFFD5D7DA))
        AgendaThemeStyle.MULTICOLOR -> listOf(Color(0xFF5367C7), Color(0xFFDB5E87), Color(0xFF3C9B72))
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEach { color -> Box(Modifier.size(34.dp).background(color, RoundedCornerShape(10.dp))) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(style.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(style.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = language.text("Seleccionado", "Selected"), tint = colors[1])
        }
    }
}
