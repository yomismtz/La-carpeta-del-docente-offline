# Cuaderno del maestro📓

Aplicación Android para docentes. Funciona como un cuaderno digital configurable para cualquier materia.

## Incluido en esta versión 1

- Perfil del docente: nombre, fecha de nacimiento, grado profesional, institución y correo.
- Periodos escolares: trimestre, cuatrimestre, semestre, bimestre u otro.
- Cierre de periodo sin borrar datos previos.
- Opción de copiar rubros y rúbricas al siguiente periodo.
- Base de alumnos: nombre, matrícula/ID, correo, teléfono, nacimiento, grupo, clínica/sección/salón, equipo y observaciones.
- Pase de lista por fecha.
- Estados: presente, falta, retardo y justificada.
- Una clase suspendida/no trabajada no entra al denominador de asistencia.
- Cálculo automático del porcentaje de asistencia sobre días realmente trabajados.
- Rubros de evaluación totalmente editables.
- Validación visual de que los rubros finales sumen 100%.
- Rúbrica interna independiente para cada rubro.
- Validación de que cada rúbrica sume 100%.
- Captura de calificaciones de 0 a 100.
- Calificación mediante criterios de rúbrica y cálculo automático del rubro.
- Calificación final ponderada (porcentaje y conversión visual a escala 0–10).
- Calendario/agenda de fechas importantes.
- Cumpleaños automáticos a partir de la fecha de nacimiento de los alumnos.
- Reporte grupal y exportación CSV.
- Diseño inspirado en cuaderno, con iconos y paleta morado/menta/turquesa.
- Rotación Android habilitada: vertical y horizontal.

## Estructura de evaluación

Ejemplo:

- Exámenes 40%
- Actividades 20%
- Proyecto 25%
- Exposición 10%
- Participación 5%
- TOTAL 100%

Cada rubro puede tener una rúbrica propia que también debe sumar 100%.

## Abrir en Android Studio

1. Descomprime la carpeta.
2. Android Studio > Open > selecciona `ProfeCuaderno`.
3. Permite que Gradle sincronice las dependencias.
4. Ejecuta en un dispositivo Android o emulador (mínimo Android 8 / API 26).

## Generar APK con GitHub Actions

El repositorio incluye `.github/workflows/build-apk.yml`. Si subes el proyecto a GitHub, el workflow compila automáticamente `app-debug.apk` y lo deja como artefacto de Actions.

## Nota de privacidad

La app guarda datos de alumnos localmente en SQLite. Antes de una publicación pública conviene añadir bloqueo por PIN/biometría, cifrado de base de datos y una política de privacidad adecuada para datos personales.

## Siguiente versión sugerida

- Bloqueo por PIN/biometría.
- Importar alumnos desde CSV/Excel.
- Exportar PDF además de CSV.
- Configurar cuánto vale un retardo o una falta justificada.
- Plantillas de evaluación reutilizables.
- Copias de seguridad cifradas.
- Estadísticas y alertas de alumnos en riesgo.
