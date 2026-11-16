# Plan Ing. Software · v2.1 (Android)

App personal para seguir un plan de 12 semanas: estudio por hitos (pistas A y B), entrenamiento, agenda con
avisos, tiempo en pantalla con límites por app y un widget. Todo el progreso vive en tu teléfono; opcionalmente
se envía a una hoja de Google.

## Compilar

**Con GitHub Actions (lo normal):** sube el contenido de esta carpeta (incluida `.github/`), ve a
*Actions → Build APK → Run workflow* y descarga:

- `PlanIngSoftware-debug`: el de siempre. Se instala encima del que ya tienes.
- `PlanIngSoftware-release`: más pequeño y rápido (R8). Sin configurar nada se firma con la clave de depuración,
  así que también se instala encima del actual. Si algo falla en él, usa el debug.

Cada ejecución también corre las pruebas (`app/src/test`) y el *lint*. Si subes una etiqueta `v2.1.0`, el APK
release se adjunta automáticamente a una *Release* de GitHub.

**Firma propia (opcional):** crea un keystore y guarda en *Settings → Secrets → Actions*:
`KEYSTORE_BASE64` (`base64 -w0 mi.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Ojo: un APK firmado con otra clave **no se instala encima** de uno firmado con la clave de depuración;
habría que desinstalar (exporta antes tu copia de seguridad).

**En local:** `gradle assembleDebug` (Gradle 8.9, JDK 17). Para generar el *wrapper* una vez:
`gradle wrapper --gradle-version 8.9` y sube `gradlew`, `gradlew.bat` y `gradle/wrapper/`.

## Pestañas
- **Hoy**: franja de la semana, agenda del día y check rápido. Toca un bloque para editarlo (candado).
- **Estudio**: HOY TOCA de las pistas A y B y los cursos con sus hitos.
- **Workout**: sesión del día según tu semana y fase, y registro de sesiones.
- **Progreso**: sección Estudio y sección Ejercicio.
- **Foco**: tiempo en pantalla por app, límites diarios y bloqueo al llegar al límite.
- **⚙ (en Hoy)**: avisos, Google Sheets, copia de seguridad, diagnóstico, plan y contraseña.

## Google Sheets
Pasos en [`google-sheets/LEEME.md`](google-sheets/LEEME.md). Resumen: pegas `google-sheets/Codigo.gs` en tu hoja,
lo publicas como aplicación web, y pones la URL y un token en *Ajustes → Google Sheets*. La app escribe las
pestañas Resumen, Hitos, Bloques, Entreno, Tests y Foco, y guarda un respaldo restaurable.

## Copia de seguridad
*Ajustes → Copia de seguridad* exporta/importa un archivo `.json` con tu progreso y ajustes (sin contraseña).
También se respalda con la copia automática de Android y con «Restaurar de la hoja».

## Candado
Cambiar horario, fecha de inicio, límites o restaurar datos pide contraseña o resolver retos (cálculo mental,
apaga las luces, torres de Hanói). La contraseña usa PBKDF2 y tras 5 fallos hay una espera creciente.
Es una barrera de fricción, no seguridad fuerte: quien desinstale la app o quite permisos puede saltarla.
La pantalla *Diagnóstico* avisa si falta algún permiso.

## Permisos
- Notificaciones, alarmas exactas y quitar ahorro de batería (para que lleguen a tiempo).
- Foco: «Acceso al uso» (medir) y «Mostrar sobre otras apps» (solo para tapar la pantalla).
- Internet: solo para Google Sheets, si lo activas.

## Notas para quien edite el plan
- **No reordenes los bloques de `plan.json`**: el progreso se guarda por posición del bloque. Añade al final.
- Los bloques que crees desde la app reciben un id nuevo que nunca se reutiliza.
- Si algún día publicas en Google Play: `USE_EXACT_ALARM`, `QUERY_ALL_PACKAGES` y `SYSTEM_ALERT_WINDOW`
  son permisos restringidos que piden justificación. Para uso personal con APK no importa.
- `plan.json` contiene tu horario personal. Si el repositorio es público, revisa que no quieras ocultar nada.

## Pruebas
`gradle testDebugUnitTest` ejecuta `PlanTest` (racha, bloques que cruzan la medianoche, fin del plan, estado global…).
La lógica del plan depende de la interfaz `Progreso`, no de Android, para poder probarla.

## Novedades de la 2.1
- Avisos: ya no se pierden los que caen a la misma hora ni los retrasados por Doze; «Hito listo» marca el hito del aviso.
- Bloques que cruzan la medianoche, fin del plan a las 12 semanas, ids de bloque que no se reutilizan.
- Foco: recupera la app abierta tras un reinicio del servicio, cuenta bien a medianoche, se pausa con la pantalla
  apagada, cachea días pasados y avisa si falta un permiso.
- Widget: botones de hito A y B con aviso y «Deshacer».
- Google Sheets, copia de seguridad en archivo, diagnóstico, tema claro/oscuro, PBKDF2.
- Build: Kotlin 2.0, AGP 8.7, SDK 35, R8, versionCode automático, pruebas y lint en CI, Dependabot.

## Nota sobre Rust
Notificaciones, widget, tiempo en pantalla y bloqueo de apps solo existen en las APIs de Android (Kotlin/Java).
