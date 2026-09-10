package com.profecuaderno.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val PROFILE_PREFS = "teacher_profile_extras"
private const val KEY_TEACHING_LEVEL = "teaching_level"

fun loadTeachingLevel(context: Context): String =
    context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).getString(KEY_TEACHING_LEVEL, "").orEmpty()

fun saveTeachingLevel(context: Context, value: String) {
    context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TEACHING_LEVEL, value).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingLevelSelector(value: String, onValueChange: (String) -> Unit) {
    val options = listOf(
        "Preescolar", "Primaria", "Secundaria", "Telesecundaria",
        "Bachillerato / Preparatoria", "Escuela técnica", "Universidad / Licenciatura",
        "Maestría", "Doctorado", "Educación especial", "Taller / Capacitación",
        "Varios niveles", "Otro"
    )
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Nivel que imparte") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false })
            }
        }
    }
}

@Composable
fun TeacherPhotoPicker() {
    // La selección de avatar se retiró para mantener el perfil más simple.
}

@Composable
fun TeacherAvatar(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Surface(shape = CircleShape, tonalElevation = 2.dp, modifier = modifier.size(size)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
