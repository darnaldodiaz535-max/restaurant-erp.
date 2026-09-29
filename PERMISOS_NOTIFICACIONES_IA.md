# Permisos, notificaciones personales y propuestas de horarios

Entrega local continuada sobre `hoy-actualizado`, sin reconstruir la aplicación. Este documento describe esta última modificación; los documentos anteriores explican entregas anteriores y no sustituyen estas reglas de permisos.

## Resultado

- Cualquier cuenta activa autenticada puede navegar por todos los módulos y consultar sus datos, áreas, personal, plazas, horarios e imágenes/evidencias.
- ADMIN, EMPRESA, JEFE_SALON, JEFE_COCINA y JEFE_LOCAL conservan administración. TRABAJADOR y EMPLEADO consultan los datos de negocio; no agregan, editan, eliminan, cambian estados ni suben imágenes. Sí pueden cambiar su propia contraseña, cerrar sesión y marcar SUS notificaciones como leídas.
- Se conserva la creación de cuentas con usuario, contraseña y rol, el hash existente y las sesiones. No se modificaron AuthController, las credenciales/configuración Oracle, Dockerfile, render.yaml ni pom.xml.
- Actividades recientes conserva exactamente la consulta de los ocho registros y su representación existentes. Solo se permite consultarla a todas las cuentas, como se pidió. No se agregó paginación ni se cambió el orden.

## Notificaciones

La campana consulta `/api/notificaciones`. La identidad siempre sale de la sesión: no se admite un ID de usuario para seleccionar otro buzón. La lista trae 50 entradas y permite ver anteriores; el contador incluye todas las no leídas. Se puede abrir, marcar una como leída o marcar todas las propias. Se actualiza al abrir y en la actualización periódica existente de 60 segundos.

Se generan avisos persistentes para:
- Responsable de una tarea creada/modificada/eliminada o reasignada (el anterior también recibe aviso al reasignar).
- Trabajador cuyo turno individual se crea/modifica/elimina.
- Responsables de incidencias y cuentas administrativas para incidencias.
- Administración cuando una tarea se completa o se envía a revisión; trabajador cuando se aprueba su evidencia.
- Destinatarios seleccionados de una imagen de horario, incluyendo reemplazo y eliminación. La selección se conserva en Oracle para futuros reemplazos. Una imagen antigua o nueva sin destinatarios no se envía indiscriminadamente a todos. En reemplazos sin selección se conservan los destinatarios anteriores.
- Empleados incluidos en un horario generado y PUBLICADO, una sola notificación por empleado por publicación.

Las alertas administrativas anteriores de inventario, tareas, compras, etc. siguen en Dashboard para administradores. La campana ahora es un buzón personal; no replica todas las actividades globales. No se inventan notificaciones retrospectivas para acciones anteriores a esta migración.

## IA: configuración pendiente

Proveedor preparado: OpenAI, Responses API con salida JSON estructurada. Todo se realiza desde Spring Boot. Referencia: https://developers.openai.com/api/docs/guides/structured-outputs

Configurar posteriormente en Render → Environment:

| Variable | Valor que debes aportar |
|---|---|
| OPENAI_API_KEY | Una clave válida de tu proyecto de OpenAI API, con cuota disponible. Guardarla únicamente como secreto del servidor. |
| OPENAI_MODEL | ID de un modelo disponible para tu cuenta que admita Responses API y Structured Outputs (`json_schema`). |

No hay claves incorporadas ni modelo ficticio. No me envíes la clave por chat. Sin estas variables el sistema permite consultar/subir horarios normalmente, pero muestra que la generación con IA está pendiente de configuración.

Se envían al proveedor las instrucciones, IDs/nombres/cargos/áreas de empleados activos de las cuatro áreas y los turnos de la semana solicitada. No se envían contraseñas, hashes, correos ni credenciales de Oracle. La petición usa `store:false`. La aplicación no da a la IA herramientas ni acceso a Oracle.

Flujo: Horarios → Generar horario con IA → elegir primer día e instrucciones → Generar propuesta → revisar/editar trabajadores, fechas y horas, agregar/quitar filas → Publicar horario. Las propuestas se conservan separadas de APP_TURNO; cada administrador puede recuperar sus últimos diez borradores. Los cambios de la vista previa se guardan al publicar; si abandona antes, recuperará el borrador original generado.

Validaciones backend: administrador y propietario del borrador, trabajadores activos y existentes con área válida, siete días seleccionados, horas HH:mm válidas, fin posterior al inicio el mismo día, duración máxima 12 horas por turno, límites de tamaño, sin solapamientos entre filas ni con APP_TURNO. Se vuelven a comprobar al publicar. La publicación transaccional agrega turnos, registra la acción y avisa solo a los afectados. Repetir la publicación del mismo borrador no duplica turnos.

La propuesta agrega turnos; nunca reemplaza automáticamente una semana existente. Los turnos nocturnos que cruzan medianoche no están admitidos por este generador. La interpretación de requisitos en lenguaje natural (dotación, descansos, etc.) debe revisarla el administrador; no se afirma una comprobación automática de todas esas condiciones ni de reglas laborales. El modelo puede devolver advertencias o una propuesta inválida que el servidor rechace.

## Oracle: SQL manual

Ejecutar únicamente `migrations/04_notificaciones_propuestas.sql` para ESTA entrega, conectado a Oracle Cloud con el esquema `RESTAPI_HOY2609`. No se ejecutó aquí contra tu base.

Crea tres tablas:
1. APP_NOTIFICACION: destinatario APP_USUARIO, módulo, mensaje, leído y fecha.
2. APP_PROPUESTA_HORARIO: propietario APP_USUARIO, inicio de semana, JSON en CLOB, estado y fecha.
3. APP_HORARIO_DESTINATARIO: relación de las imágenes existentes con los empleados a quienes corresponden.

No agrega ni modifica columnas de APP_USUARIO, APP_EMPLEADO, APP_TAREA_DIARIA, APP_TURNO ni APP_HORARIO_IMAGEN. Reutiliza APP_TURNO al publicar y el BLOB actual para fotos de horarios.

El SQL no contiene DROP TABLE, TRUNCATE, DELETE ni UPDATE de datos existentes. Tiene una comprobación del esquema e ignora creaciones de tablas/índices que ya existen. Si ya existe una tabla homónima con otra estructura, hay que revisar su definición; no la reemplaza. Oracle confirma DDL automáticamente, por lo que si un paso falla deben revisarse los objetos ya creados antes de continuar.

Las claves foráneas nuevas llevan ON DELETE CASCADE: en el futuro, si un administrador elimina explícitamente un usuario, sus notificaciones/borradores se eliminan junto con él; si elimina una imagen/empleado, sus relaciones de destinatarios se eliminan. La migración en sí no elimina nada.

Requiere las migraciones anteriores ya aplicadas (áreas/plazas/imágenes y JEFE_LOCAL). No vuelvas a ejecutar APP_ESQUEMA.sql ni scripts iniciales CREATE TABLE sobre una producción existente.

## Archivos de esta entrega

Rutas Java bajo `backend/src/main/java/cl/restaurante/api/health/`.

| Archivo | Cambio |
|---|---|
| RoleAccessService.java | Lectura autenticada para todos; escrituras administrativas. Mantiene los nombres de roles. |
| AreaService.java | Consulta de las cuatro áreas sin restricción por rol/área propia. |
| DashboardController.java | Métricas visibles para cuentas autenticadas. |
| ActivityController.java | Acceso de lectura general; alertas administrativas solo administradores; consulta de actividades intacta. |
| OracleConnectionController.java | Consulta completa de personal; escrituras siguen protegidas. |
| ModuleController.java | Lectura completa de módulos, escrituras protegidas y eventos dirigidos de tareas/turnos/incidencias. |
| PlazaController.java | Consulta de plazas para todos; gestión administrativa. |
| TaskEvidenceController.java | Consulta de evidencias para todos, modificaciones administrativas y avisos de revisión. |
| ScheduleController.java | Mantiene BLOB de imágenes; añade selección/persistencia de destinatarios y notificaciones al cambiar/eliminar. |
| NotificationService.java (nuevo) | Generación de avisos por empleado/cuenta y por roles administrativos. |
| NotificationController.java (nuevo) | Buzón personal paginado, contador y marcado como leído con propiedad validada. |
| AiScheduleClient.java (nuevo) | Llamada backend al proveedor con secretos de entorno y esquema JSON. |
| AiScheduleController.java (nuevo) | Borradores, validación, recuperación y publicación transaccional. |
| scrip.js | Menús visibles, controles de modificación según permiso y conexión con el buzón personal. |
| gestion.js | Consulta de áreas/plazas, selección de destinatarios de imágenes y entrada al generador. |
| notificaciones-horarios.js (nuevo) | Campana personal y formulario/vista previa editable/publicación de propuestas. |
| index.html | Carga el nuevo JavaScript. |
| style.css | Estilos de la campana y vista previa reutilizando los controles actuales. |
| backend/src/main/resources/static/* | Copias sincronizadas de los cinco archivos frontend anteriores para Spring Boot. |
| backend/src/test/java/cl/restaurante/api/health/PermissionsIntegrationTest.java | Pruebas de permisos, aislamiento de notificaciones, generación/publicación simulada y regresiones de autenticación. |
| migrations/04_notificaciones_propuestas.sql (nuevo) | Migración manual, sin borrar datos existentes. |
| PERMISOS_NOTIFICACIONES_IA.md (nuevo) | Este informe. |

## Cómo probar antes de producción

1. En una copia de la base aplicar la migración 04. Compilar con Java 25: `cd backend`, `mvn test`, `mvn package`.
2. Entrar como TRABAJADOR: recorrer todos los módulos, cuatro áreas, plazas, horarios y evidencias. Verificar lectura y controles administrativos deshabilitados/ocultos. Llamar directamente POST/PUT/DELETE de un módulo debe devolver 403; PATCH no implementado devuelve 405 y no cambia datos.
3. Como administrador asignar una tarea al trabajador A. Entrar como A: campana con aviso. Entrar como B: ese aviso no aparece. Marcarlo leído como A, recargar sesión y confirmar contador. Intentar marcar el ID del aviso de A con sesión B devuelve 404.
4. Subir imagen seleccionando A. Reemplazarla y eliminarla: A recibe avisos, B no. Confirmar que descargar/ver imagen sigue disponible a cualquier usuario autenticado.
5. Crear/cambiar/eliminar turno individual: notificación al trabajador correspondiente. Registrar incidencia: aviso al responsable y administración.
6. IA sin variables: mensaje de configuración pendiente, resto de Horarios funciona. Con variables válidas: generar semana con empleados reales de prueba. Antes de publicar APP_TURNO no cambia. Editar vista previa, publicar y comprobar turnos/notificaciones. Repetir publicación no debe duplicar. Probar horas inválidas/solapadas y trabajador inactivo: rechazo sin escritura parcial.
7. Probar login/logout, crear cuenta con cada rol y cambiar contraseña. Contraseña anterior rechazada y nueva aceptada. Verificar tareas, horarios, inventario y demás operaciones administrativas existentes.

## Validación realizada y límites

Ver `VALIDACION_PERMISOS_IA.md` para resultados exactos y las limitaciones de este entorno. La IA real necesita tu clave/modelo; las pruebas de generación usan una respuesta controlada, sin llamadas facturables ni datos de producción. No se ejecutó SQL en Oracle Cloud y no se realizó commit, push ni despliegue.
