package com.profecuaderno.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
fun ThemeSelectionScreen(initial: AgendaThemeStyle = AgendaThemeStyle.MINT_LAVENDER, onSelected: (AgendaThemeStyle) -> Unit, onCancel: (() -> Unit)? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("agenda_preferences", 0) }
    var selected by remember(initial) { mutableStateOf(initial) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("ui_dark", false)) }
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1f)) }
    var fontStyle by remember { mutableStateOf(AppFontStyle.fromKey(prefs.getString("font_style", null))) }
    val language = LocalAppLanguage.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Palette, null, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(10.dp))
            Column { Text(language.text("Apariencia", "Appearance"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(language.text("Color, modo y tipografía.", "Color, mode and typography.")) }
        }

        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Language,null); Spacer(Modifier.width(8.dp)); Text("Idioma / Language", style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { AppLanguage.entries.forEach { item -> FilterChip(selected=language==item,onClick={ if(language!=item){AppLanguagePrefs.save(context,item);(context as? Activity)?.recreate()} },label={Text(item.label)},modifier=Modifier.weight(1f)) } }
        } }

        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(language.text("Modo oscuro","Dark mode"),fontWeight=FontWeight.Bold);Text(language.text("También puedes usar modo claro.","You can also use light mode."),style=MaterialTheme.typography.bodySmall)}; Switch(checked=darkMode,onCheckedChange={darkMode=it}) }
            Text(language.text("Tamaño de letra","Font size"),fontWeight=FontWeight.Bold)
            Slider(value=fontScale,onValueChange={fontScale=it},valueRange=.85f..1.35f,steps=4)
            Text(language.text("Estilo de letra","Font style"),fontWeight=FontWeight.Bold)
            AppFontStyle.entries.forEach { f -> FilterChip(selected=fontStyle==f,onClick={fontStyle=f},label={Text(f.label)},modifier=Modifier.fillMaxWidth()) }
        } }

        Text(language.text("Paleta de color","Color palette"), style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        AgendaThemeStyle.entries.forEach { style -> ThemePreviewCard(style, style==selected){selected=style} }

        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(onCancel!=null) OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f).height(54.dp)){Text(language.text("Cancelar","Cancel"))}
            Button(onClick={ prefs.edit().putBoolean("ui_dark",darkMode).putFloat("font_scale",fontScale).putString("font_style",fontStyle.key).apply(); onSelected(selected); (context as? Activity)?.recreate() },modifier=Modifier.weight(1f).height(54.dp)){Text(language.text("Aplicar","Apply"),fontWeight=FontWeight.SemiBold)}
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ThemePreviewCard(style: AgendaThemeStyle, selected:Boolean, onClick:()->Unit) {
    val colors=when(style){
        AgendaThemeStyle.GRAPHITE_BLUE->listOf(Color(0xFF0B4F6C),Color(0xFF263238),Color(0xFF90A4AE)); AgendaThemeStyle.SUNSET_GARDEN->listOf(Color(0xFFC99500),Color(0xFF2E6B3A),Color(0xFF8A3B12)); AgendaThemeStyle.BOLD_CLASSIC->listOf(Color(0xFF003F88),Color(0xFFD14900),Color(0xFFB00020)); AgendaThemeStyle.MINT_LAVENDER->listOf(Color(0xFF6B3FA0),Color(0xFF008C7A),Color(0xFF007C91)); AgendaThemeStyle.PINK_BLUE->listOf(Color(0xFFB00063),Color(0xFF2367B1),Color(0xFF6A3D7C)); AgendaThemeStyle.GRAYSCALE->listOf(Color(0xFF202124),Color(0xFF777777),Color(0xFFDDDDDD)); AgendaThemeStyle.MULTICOLOR->listOf(Color(0xFF3F37C9),Color(0xFFE45756),Color(0xFF2A9D65)) }
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick=onClick), shape=RoundedCornerShape(20.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){ Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){colors.forEach{Box(Modifier.size(34.dp).background(it,RoundedCornerShape(9.dp)))}};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(style.title,fontWeight=FontWeight.SemiBold);Text(style.subtitle,style=MaterialTheme.typography.bodySmall)};if(selected)Icon(Icons.Default.CheckCircle,"Seleccionado") } }
}
