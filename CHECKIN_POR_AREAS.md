# MariGex — Check-in por áreas

Implementación del 3 de noviembre de 2026 en `C:\Users\darna\OneDrive\Desktop\version 1.1.4`.

## Funcionamiento

- Cuatro áreas independientes: Cocina, Salón, Barra y Copería.
- Una tarea por plantilla, área y período; no se multiplica por cada trabajador. Las tareas DIARIA se generan por fecha y las SEMANAL una vez por semana de lunes a domingo.
- Todos los usuarios autenticados pueden consultar tareas, fechas anteriores y fotografías de las cuatro áreas.
- Solo JEFE_COCINA, JEFE_SALON, JEFE_BARRA y JEFE_COPERIA confirman las tareas de su área. ADMIN/EMPRESA supervisan y envían observaciones.
- SIMPLE: confirmación de checklist. FOTO: seleccionar el archivo y pulsar «Enviar evidencia y completar». La foto y la confirmación se guardan en una misma transacción. No se completa sin evidencia válida.
- JPG, PNG y WebP hasta 4 MB, con validación de firma y tipo. Se registra cuenta, nombre del responsable y fecha/hora. Los instantes se muestran con la hora registrada por el servidor/base.
- Las tareas DIARIA se confirman solo en su fecha. Las SEMANAL pueden confirmarse durante su semana completa en America/Santiago. Los períodos anteriores no se reabren.
- Reportes semanales de lunes a domingo, permanentes, con total, completadas, pendientes, porcentaje, responsables y fotografías. Administración puede consultar el conjunto o un área y ve primero las áreas con más pendientes. Cada jefe solo consulta su propia área.
- El cierre guarda un resumen por área y semana. El detalle usa los registros diarios persistentes; las plantillas no cambian el contenido de registros ya generados. No se agregan tareas a semanas cerradas.
- Se destacan las áreas con tareas asignadas y ninguna completada; las áreas sin plantillas muestran «Sin tareas», sin inventar incumplimientos.
- Los pendientes y las tareas sin fotografía obligatoria aparecen en los reportes. Una tarea pendiente al cerrar la semana permanece pendiente/incumplida, nunca se completa por el paso del tiempo.
- Sugerencias con remitente, destinatario, fecha y contenido; se conserva historial por área. Si hay varios jefes activos del mismo rol, cada uno recibe un registro y aviso. Sin jefe activo, el envío se rechaza para evitar perder el mensaje.
- Avisos internos de reportes e incumplimientos (incluidos en el detalle del resumen) y observaciones. Este flujo no encola correo ni Push ni depende de preferencias de servicios externos.

## Limpieza profunda semanal KALI

Ver LIMPIEZA_SEMANAL_KALI.md para las 31 tareas y sus marcas. Para esta ampliación: ejecutar primero la migración 22 (esquema), publicar manualmente el backend semanal y después ejecutar la migración 23 (tareas). Este orden evita que la versión diaria anterior genere copias diarias de estas tareas.

## Oracle: aplicar antes de usar esta versión

No se ejecutó ningún SQL contra Oracle ni se accedió a datos de producción.

1. En Oracle Database Actions, con el propietario del esquema APP_, comprobar que ya están aplicadas las migraciones 08 y 17 y que existe APP_NOTIFICACION con DETALLE, LEIDA y VISIBLE.
2. Ejecutar `migrations/20_checkin_por_areas.sql` y luego `migrations/21_checkin_coperia.sql` como scripts completos, incluidos los bloques PL/SQL y `/`. Si ya se ejecutó la 20, solo falta la 21.
3. Revisar los SELECT finales. APP_CHECKIN_CONFIG.INICIO fija la fecha de activación en America/Santiago. El nuevo historial comienza en esa fecha; no convierte el cumplimiento personal antiguo en cumplimiento por área.
4. Crear/asignar las cuentas de los jefes. Personal → Crear cuenta utiliza «Jefe de copería». La migración 21 convierte únicamente el rol JEFE_COOPERATIVA creado con el nombre incorrecto a JEFE_COPERIA; conserva las demás cuentas.
5. Incorporar las tareas reales a APP_CHECKIN_PLANTILLA cuando se disponga de la lista definitiva.

El script crea APP_CHECKIN_CONFIG, APP_CHECKIN_REGISTRO, APP_CHECKIN_EVIDENCIA, APP_CHECKIN_RESUMEN y APP_CHECKIN_OBSERVACION, con sus restricciones e índices. Amplía las restricciones de plantillas y roles. Su reejecución no borra datos ni reinicia la fecha de activación. Oracle confirma DDL implícitamente: aplicar primero en una copia de prueba. El bloque también confirma la fila de configuración con COMMIT.

### Cargar las tareas definitivas

Se reutiliza APP_CHECKIN_PLANTILLA. La lista real de limpieza profunda semanal y sus requisitos de fotografía están transcritos en LIMPIEZA_SEMANAL_KALI.md y migrations/23_limpieza_profunda_semanal_kali.sql. No se ejecutó esa carga contra Oracle desde el agente. Las tareas de prueba solo se insertan en H2 efímero.

Usar los valores reales como parámetros, por ejemplo:

```sql
INSERT INTO APP_CHECKIN_PLANTILLA
  (AREA,TAREA,METODO_COMPLETADO,VIGENTE_DESDE,VIGENTE_HASTA,POSICION,ACTIVA)
VALUES
  (:area,:nombre_real,:metodo,TO_DATE(:desde,'YYYY-MM-DD'),NULL,:posicion,'S');
```

`:area`: COCINA, SALON, BARRA o COPERIA. `:metodo`: SIMPLE o FOTO, según tu lista. No cambiar retrospectivamente plantillas de períodos cerrados. La columna FRECUENCIA admite DIARIA (predeterminada para registros anteriores) y SEMANAL. La limpieza profunda KALI usa SEMANAL.

Se reutilizan COPERIA y JEFE_COPERIA del resto de MariGex. La migración 21 corrige el nombre usado por error en la migración 20, conservando los registros.

## Generación automática e historial anterior

CheckinJobs ejecuta la reconciliación por área cada minuto, con el interruptor existente `marigex.jobs.enabled`. Recupera las fechas omitidas por pausas/reinicios desde la activación y cierra las semanas terminadas. La consulta de tareas y reportes también reconcilia. Las claves únicas y el bloqueo transaccional de la configuración evitan cierres y avisos duplicados.

Los registros personales antiguos APP_CHECKIN_DIA/TAREA/FOTO/ENVIO_SEMANAL se conservan. No se migran automáticamente al nuevo historial porque representan tareas por persona. Los endpoints antiguos de escritura devuelven 403 para cualquier rol: una interfaz anterior no permite eludir los permisos nuevos. Los reportes personales antiguos solo están disponibles a administración. La antigua purga de fotos se eliminó; las fotografías que ya habían sido purgadas no se pueden reconstruir.

El frontend que empaqueta Spring Boot está en `backend/src/main/resources/static`. Los archivos de prototipo de la raíz no son el frontend del JAR. Se actualizó la versión de caché del service worker.

## Comprobación manual

1. Aplicar la migración en un entorno de prueba. Cargar allí las tareas reales autorizadas y disponer de cuentas de trabajador, cuatro jefes y administrador.
2. Trabajador: abrir Check-in y recorrer las cuatro áreas. Debe ver estados y fotografías, sin controles para completar tareas ni acceso al resumen semanal/observaciones.
3. Jefe: en su área ve controles; en otras solo consulta tareas. Un checklist guarda nombre y hora. Una tarea FOTO exige seleccionar la foto y enviarla. Comprobar el rechazo de archivos inválidos y superiores a 4 MB.
4. Intentar el endpoint de confirmación de otra área con la sesión del jefe: 403. Con trabajador o administrador: 403. No repetir una completada ni modificar fechas anteriores.
5. Administrador: abrir «Resumen semanal de Check-in», alternar resumen general e individual, consultar semana en curso e historial. Desplegar tareas y fotografías; revisar pendientes y áreas sin completadas.
6. Jefe: el resumen y las observaciones pertenecen únicamente a su área, incluso cambiando parámetros de la URL.
7. Administrador: en «Sugerencias y observaciones», seleccionar área y enviar un mensaje. El jefe debe encontrarlo en su historial y en las notificaciones internas. Repetir sin jefe activo: debe indicar que no hay destinatario.
8. Tras un cierre semanal, confirmar que se conserva el reporte y la evidencia al reiniciar el servidor. Consultar una fecha anterior desde «Tareas e historial».
9. Revisar la interfaz en computador y teléfono. Se reutilizan los componentes y reglas adaptables existentes, con ajuste de filas y formularios. La validación visual en dispositivos reales queda pendiente.

## Pruebas realizadas

Java 25; compilación del backend y pruebas con H2 en modo Oracle, perfiles test y trabajos programados desactivados. Nunca se usan las credenciales de application-local.properties.

- AreaCheckinIntegrationTest: 7 pruebas aprobadas, incluidas periodicidad semanal y carga idempotente de las 31 tareas reales con los 19 requisitos de fotografía. Incluyen acceso a las cuatro áreas, permisos de los cuatro roles, prohibición de escritura antigua, foto obligatoria/válida, autoría, cierre e idempotencia, conservación de evidencias, historial, reportes limitados por área y observaciones internas con destinatarios.
- WebPushEncryptionTest: 1 prueba aprobada.
- Suite completa: las 36 pruebas de PermissionsIntegrationTest tienen 2 fallos y 9 errores. Se ejecutó también una copia limpia del HEAD anterior en una carpeta temporal: los mismos 11 casos y errores ya existían. Nueve dependen de APP_NOTIF_MODULO_PREF ausente en el esquema de esa suite; dos esperan 200/403 en endpoints de otros módulos que ya devuelven 410. No se cambiaron esos módulos ni se ocultaron los fallos.
- `node --check backend/src/main/resources/static/scrip.js`: aprobado.
- `git diff --check`: aprobado.
- Empaquetado `mvn -f backend/pom.xml package -DskipTests`: aprobado después de ejecutar las pruebas seleccionadas. JAR Spring Boot generado correctamente.
- No se ejecutó la migración PL/SQL en Oracle real. El esquema CREATE TABLE nuevo se prueba en H2 Oracle; eso no sustituye verificar el script completo en Oracle de prueba.
- No se hicieron pruebas visuales ni despliegues en Render. El flujo de negocio está probado mediante MockMvc; la lista KALI fue proporcionada y su carga se verifica en H2.

Maven no estaba en PATH. Se descargó Maven 3.9.11 a una carpeta temporal. La verificación inicial en línea encontró un problema de certificados Java; se ejecutaron las pruebas en modo offline con las dependencias existentes y un archivo settings temporal que reconoce los dos identificadores de repositorio de la caché. No se desactivó TLS ni se cambió el pom o configuración del proyecto para resolverlo.

Con Maven configurado en tu terminal:

```powershell
mvn -f backend/pom.xml test '-Dtest=AreaCheckinIntegrationTest,WebPushEncryptionTest'
mvn -f backend/pom.xml test
node --check backend/src/main/resources/static/scrip.js
```

La segunda orden conserva los fallos anteriores indicados. No afirmar que toda la suite está verde.

## Archivos de esta entrega

Nuevos:
- backend/src/main/java/cl/restaurante/api/health/AreaCheckinService.java
- backend/src/main/java/cl/restaurante/api/health/AreaCheckinController.java
- backend/src/test/java/cl/restaurante/api/health/AreaCheckinIntegrationTest.java
- backend/src/test/resources/checkin-area-schema.sql
- migrations/20_checkin_por_areas.sql
- migrations/21_checkin_coperia.sql
- CHECKIN_POR_AREAS.md

Modificados:
- backend/src/main/java/cl/restaurante/api/health/AuthController.java
- backend/src/main/java/cl/restaurante/api/health/RoleAccessService.java
- backend/src/main/java/cl/restaurante/api/health/NotificationService.java
- backend/src/main/java/cl/restaurante/api/health/CheckinController.java
- backend/src/main/java/cl/restaurante/api/health/CheckinService.java
- backend/src/main/java/cl/restaurante/api/health/CheckinJobs.java
- backend/src/main/resources/static/index.html
- backend/src/main/resources/static/scrip.js
- backend/src/main/resources/static/style.css
- backend/src/main/resources/static/service-worker.js
- CHECKIN_DIARIO.md

El archivo no seguido `backend/src/main/resources/application-local.properties` ya existía al comenzar. No se modificó ni se incluye entre los archivos a agregar al commit.

## Revisar, commit y push desde Visual Studio Code

No se ejecutaron git commit, git push ni despliegues. En VS Code abrir la carpeta `version 1.1.4` y usar Terminal → Nueva terminal. Revisar:

```powershell
Set-Location -LiteralPath 'C:\Users\darna\OneDrive\Desktop\version 1.1.4'
git status --short
git diff --stat
git diff
```

En Control de código fuente, revisar también los archivos nuevos, que no aparecen en `git diff` hasta agregarlos. Preparar **solo los archivos de la entrega enumerados arriba** con el botón + junto a cada archivo. Dejar fuera application-local.properties. No usar «Preparar todos» para evitar incluir configuración local.

Después de revisar los archivos preparados, tú puedes ejecutar:

```powershell
git diff --cached --stat
git diff --cached --check
git diff --cached
git commit -m "feat: implementar check-in por areas con reportes y permisos"
git branch --show-current
git remote -v
git push
```

Si la rama no tiene upstream, sustituir `<rama>` por el resultado de `git branch --show-current` y usar `git push --set-upstream origin <rama>`. Confirmar que origin sea tu repositorio antes de hacerlo. Estos comandos son instrucciones para tu ejecución manual.

Aplicar el SQL antes de publicar esta versión. Después de tu push, revisar Render: arranque correcto, acceso a las nuevas tablas, ausencia de errores ORA- y de advertencias repetidas de reconciliación, y comprobar los flujos por rol descritos arriba.
