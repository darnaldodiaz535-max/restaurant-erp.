# Análisis previo de hoy.zip

Se revisaron todos los controladores Java, configuración, POM, SQL, HTML, JavaScript y CSS del ZIP antes de editar. El proyecto usa Java 25 / Spring Boot 4.1.1, JdbcTemplate, sesiones HttpSession y contraseñas PBKDF2. No usa React ni una API externa de almacenamiento.

El frontend raíz y backend/src/main/resources/static son copias idénticas. Docker compila backend y sirve la copia static junto a /api. Ambas copias deben mantenerse sincronizadas.

## Reutilización
- Personal: APP_EMPLEADO y /api/empleados; se conservan estadísticas, perfil y campos existentes.
- Roles: ADMIN/EMPRESA son propietarios; JEFE_SALON y JEFE_COCINA mantienen funciones operativas delegadas; EMPLEADO/TRABAJADOR son trabajadores. No se renombran roles ni se cambia el login.
- Tareas: APP_TAREA_DIARIA + APP_EVIDENCIA_TAREA; se añade solamente AREA a la tarea para que conserve su área aunque cambie el empleado. Se mantiene completar con foto y revisión.
- Horarios: el nombre visual Turnos cambia a Horarios; /api/modulos/turnos y APP_TURNO se conservan. La imagen necesita una estructura distinta: APP_HORARIO_IMAGEN con BLOB, metadatos y autor. Es el mismo patrón persistente de las evidencias existentes, sin disco de Render ni nuevas credenciales.
- Áreas: se reutiliza APP_EMPLEADO.AREA; Cocina conserva sus preparaciones APP_COCINA y añade resumen del área. Salón, Barra y Copería no duplican empleados ni tareas.
- Plazas: CARGO describe empleados existentes, pero no permite representar vacantes, cupos y puestos inactivos. APP_PLAZA añade esa planificación; ocupación calculada desde CARGO + AREA, sin migrar fichas ni cambiar el formulario Personal.
- Notificaciones: APP_ACTIVIDAD, ActivityService y campana existentes; se muestran eventos de tareas/incidencias y se actualizan periódicamente.

## Hallazgos de permisos
Los endpoints genéricos validan el módulo, pero no siempre el propietario del registro al actualizar/eliminar. Cocina puede intentar modificar registros de otra área; trabajadores pueden actualizar asistencia/producción ajenas si conocen el ID. Se incorporan controles de registro. Personal, Configuración y Horarios se reservan a ADMIN/EMPRESA para modificaciones. El trabajador puede consultar horarios, reportar sus incidencias y completar tareas asignadas de su área.

## Límites
Este análisis se basa en el ZIP, no en consultas a la base de producción. Las migraciones son manuales, aditivas y para RESTAPI_HOY2609. No se ejecutan en Oracle Cloud. Se preservan los archivos de conexión, Dockerfile y render.yaml. Los scripts antiguos de instalación se conservan como referencia, pero NO deben repetirse para actualizar.
