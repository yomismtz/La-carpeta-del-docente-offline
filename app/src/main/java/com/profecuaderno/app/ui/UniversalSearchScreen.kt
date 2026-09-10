package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.data.TrashStore

data class UniversalSearchResult(
    val title: String,
    val subtitle: String,
    val type: String,
    val groupId: Long = 0L
)

@Composable
fun UniversalSearchScreen(
    db: TeacherDbHelper,
    refresh: Int,
    onOpen: (UniversalSearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(refresh) { TrashStore.visiblePeriods(db) }

    val allResults = remember(refresh, groups.map { it.id }) {
        buildList {
            groups.forEach { group ->
                add(UniversalSearchResult(group.name, "Grupo · ${group.type}", "GROUP", group.id))

                db.getStudents(group.id).forEach { student ->
                    add(
                        UniversalSearchResult(
                            title = student.name,
                            subtitle = "Estudiante · ${group.name}${student.studentCode.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                            type = "STUDENT",
                            groupId = group.id
                        )
                    )
                }

                db.getEvents(group.id).forEach { event ->
                    add(
                        UniversalSearchResult(
                            title = event.title,
                            subtitle = "Evento · ${group.name} · ${event.date}",
                            type = "EVENT",
                            groupId = group.id
                        )
                    )
                }

                db.getCategories(group.id).forEach { category ->
                    add(
                        UniversalSearchResult(
                            title = category.name,
                            subtitle = "Rubro de evaluación · ${group.name}",
                            type = "CATEGORY",
                            groupId = group.id
                        )
                    )
                    db.getAssessmentItems(category.id).forEach { item ->
                        add(
                            UniversalSearchResult(
                                title = item.name,
                                subtitle = "Actividad / examen · ${category.name} · ${group.name}",
                                type = "ASSESSMENT",
                                groupId = group.id
                            )
                        )
                    }
                    db.getRubricCriteria(category.id).forEach { criterion ->
                        add(
                            UniversalSearchResult(
                                title = criterion.name,
                                subtitle = "Criterio de rúbrica · ${category.name} · ${group.name}",
                                type = "RUBRIC",
                                groupId = group.id
                            )
                        )
                    }
                }
            }
        }
    }

    val normalized = query.trim()
    val results = if (normalized.length < 2) emptyList() else allResults.filter {
        it.title.contains(normalized, ignoreCase = true) || it.subtitle.contains(normalized, ignoreCase = true)
    }.take(100)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar en todo el portafolio") },
            placeholder = { Text("Estudiante, grupo, evento, examen, actividad o rúbrica") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        when {
            query.isBlank() -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Búsqueda universal", style = MaterialTheme.typography.titleMedium)
                        Text("La búsqueda funciona sin internet y consulta los datos guardados en este dispositivo.")
                    }
                }
            }
            query.trim().length < 2 -> Text("Escribe al menos 2 caracteres.", style = MaterialTheme.typography.bodySmall)
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No encontré coincidencias.") }
            else -> {
                Text("${results.size} resultados", style = MaterialTheme.typography.labelLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { "${it.type}-${it.groupId}-${it.title}-${it.subtitle}" }) { result ->
                        ElevatedCard(onClick = { onOpen(result) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(result.title, style = MaterialTheme.typography.titleSmall)
                                Text(result.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
