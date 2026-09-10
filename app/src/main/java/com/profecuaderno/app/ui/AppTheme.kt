package com.profecuaderno.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

enum class AgendaThemeStyle(val key: String, val title: String, val subtitle: String) {
    GRAPHITE_BLUE("graphite_blue", "Grafito azul", "Azul petróleo, acero y grafito"),
    SUNSET_GARDEN("sunset_garden", "Jardín solar", "Mostaza, verde bosque y terracota"),
    BOLD_CLASSIC("bold_classic", "Clásico vivo", "Azul intenso, rojo y naranja"),
    MINT_LAVENDER("mint_lavender", "Menta lavanda", "Violeta, menta y turquesa"),
    PINK_BLUE("pink_blue", "Rosa azul", "Magenta, azul cielo y ciruela"),
    GRAYSCALE("grayscale", "Escala de grises", "Blanco, carbón y plata"),
    MULTICOLOR("multicolor", "Multicolor", "Índigo, coral, verde y amarillo");

    companion object { fun fromKey(key: String?): AgendaThemeStyle? = entries.firstOrNull { it.key == key } }
}

enum class AppFontStyle(val key: String, val label: String) {
    SANS("sans", "Sans serif"), SERIF("serif", "Serif"), MONO("mono", "Monoespaciada"), CURSIVE("cursive", "Manuscrita");
    companion object { fun fromKey(key: String?): AppFontStyle = entries.firstOrNull { it.key == key } ?: SANS }
}

private fun lightScheme(style: AgendaThemeStyle) = when (style) {
    AgendaThemeStyle.GRAPHITE_BLUE -> lightColorScheme(primary=Color(0xFF0B4F6C),secondary=Color(0xFF6B7C8F),tertiary=Color(0xFF263238),background=Color(0xFFF2F6F8),surface=Color.White,primaryContainer=Color(0xFFCDEAF5),secondaryContainer=Color(0xFFE0E6EB))
    AgendaThemeStyle.SUNSET_GARDEN -> lightColorScheme(primary=Color(0xFF8A3B12),secondary=Color(0xFF2E6B3A),tertiary=Color(0xFFC99500),background=Color(0xFFFFF7E8),surface=Color(0xFFFFFCF5),primaryContainer=Color(0xFFFFD5C2),secondaryContainer=Color(0xFFD5EAD8))
    AgendaThemeStyle.BOLD_CLASSIC -> lightColorScheme(primary=Color(0xFF003F88),secondary=Color(0xFFD14900),tertiary=Color(0xFFB00020),background=Color(0xFFFFF8F4),surface=Color.White,primaryContainer=Color(0xFFD5E8FF),secondaryContainer=Color(0xFFFFDCC9))
    AgendaThemeStyle.MINT_LAVENDER -> lightColorScheme(primary=Color(0xFF6B3FA0),secondary=Color(0xFF008C7A),tertiary=Color(0xFF007C91),background=Color(0xFFFBF8FF),surface=Color.White,primaryContainer=Color(0xFFEADCFB),secondaryContainer=Color(0xFFCFEFE9))
    AgendaThemeStyle.PINK_BLUE -> lightColorScheme(primary=Color(0xFFB00063),secondary=Color(0xFF2367B1),tertiary=Color(0xFF6A3D7C),background=Color(0xFFFFF7FC),surface=Color.White,primaryContainer=Color(0xFFFFD5E8),secondaryContainer=Color(0xFFD7E9FF))
    AgendaThemeStyle.GRAYSCALE -> lightColorScheme(primary=Color(0xFF202124),secondary=Color(0xFF5F6368),tertiary=Color(0xFF9AA0A6),background=Color(0xFFF5F5F5),surface=Color.White,primaryContainer=Color(0xFFE0E0E0),secondaryContainer=Color(0xFFECECEC))
    AgendaThemeStyle.MULTICOLOR -> lightColorScheme(primary=Color(0xFF3F37C9),secondary=Color(0xFFE45756),tertiary=Color(0xFF2A9D65),background=Color(0xFFFFFBF1),surface=Color.White,primaryContainer=Color(0xFFE1DFFF),secondaryContainer=Color(0xFFFFDAD8))
}

private fun darkScheme(style: AgendaThemeStyle) = when (style) {
    AgendaThemeStyle.GRAPHITE_BLUE -> darkColorScheme(primary=Color(0xFF75D1F0),secondary=Color(0xFFAFC4D6),tertiary=Color(0xFFCFD8DC),background=Color(0xFF0E1519),surface=Color(0xFF151E23),primaryContainer=Color(0xFF123E50))
    AgendaThemeStyle.SUNSET_GARDEN -> darkColorScheme(primary=Color(0xFFFFA277),secondary=Color(0xFF8CD49A),tertiary=Color(0xFFFFD05A),background=Color(0xFF19130D),surface=Color(0xFF241B13),primaryContainer=Color(0xFF5B260C))
    AgendaThemeStyle.BOLD_CLASSIC -> darkColorScheme(primary=Color(0xFF8DC1FF),secondary=Color(0xFFFFA06A),tertiary=Color(0xFFFF8B98),background=Color(0xFF121417),surface=Color(0xFF1B1E22),primaryContainer=Color(0xFF103B67))
    AgendaThemeStyle.MINT_LAVENDER -> darkColorScheme(primary=Color(0xFFCAB0F0),secondary=Color(0xFF65D9C6),tertiary=Color(0xFF75D7E5),background=Color(0xFF151219),surface=Color(0xFF211B27),primaryContainer=Color(0xFF45296B))
    AgendaThemeStyle.PINK_BLUE -> darkColorScheme(primary=Color(0xFFFF9BCB),secondary=Color(0xFF9CC7FF),tertiary=Color(0xFFD7A6E8),background=Color(0xFF181216),surface=Color(0xFF241A21),primaryContainer=Color(0xFF64123E))
    AgendaThemeStyle.GRAYSCALE -> darkColorScheme(primary=Color(0xFFE8EAED),secondary=Color(0xFFBDC1C6),tertiary=Color(0xFF9AA0A6),background=Color(0xFF111111),surface=Color(0xFF1B1B1B),primaryContainer=Color(0xFF333333))
    AgendaThemeStyle.MULTICOLOR -> darkColorScheme(primary=Color(0xFFB8B3FF),secondary=Color(0xFFFFA39F),tertiary=Color(0xFF7CDBAA),background=Color(0xFF151319),surface=Color(0xFF201D25),primaryContainer=Color(0xFF302A73))
}

private fun appTypography(scale: Float, style: AppFontStyle): Typography {
    val family = when (style) { AppFontStyle.SANS -> FontFamily.SansSerif; AppFontStyle.SERIF -> FontFamily.Serif; AppFontStyle.MONO -> FontFamily.Monospace; AppFontStyle.CURSIVE -> FontFamily.Cursive }
    val base = Typography()
    fun TextStyle.scaled() = copy(fontFamily = family, fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge=base.displayLarge.scaled(), displayMedium=base.displayMedium.scaled(), displaySmall=base.displaySmall.scaled(),
        headlineLarge=base.headlineLarge.scaled(), headlineMedium=base.headlineMedium.scaled(), headlineSmall=base.headlineSmall.scaled(),
        titleLarge=base.titleLarge.scaled(), titleMedium=base.titleMedium.scaled(), titleSmall=base.titleSmall.scaled(),
        bodyLarge=base.bodyLarge.scaled(), bodyMedium=base.bodyMedium.scaled(), bodySmall=base.bodySmall.scaled(),
        labelLarge=base.labelLarge.scaled(), labelMedium=base.labelMedium.scaled(), labelSmall=base.labelSmall.scaled()
    )
}

fun agendaPaperColor(style: AgendaThemeStyle, dark: Boolean = false): Color = if (dark) darkScheme(style).background else lightScheme(style).background
fun agendaRuleColor(style: AgendaThemeStyle, dark: Boolean = false): Color = if (dark) darkScheme(style).outline.copy(alpha=.28f) else lightScheme(style).outline.copy(alpha=.22f)

@Composable
fun ProfeCuadernoTheme(style: AgendaThemeStyle = AgendaThemeStyle.MINT_LAVENDER, darkMode: Boolean = false, fontScale: Float = 1f, fontStyle: AppFontStyle = AppFontStyle.SANS, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkMode) darkScheme(style) else lightScheme(style), typography = appTypography(fontScale.coerceIn(.85f, 1.35f), fontStyle), content = content)
}
