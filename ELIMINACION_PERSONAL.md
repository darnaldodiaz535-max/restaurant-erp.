# Eliminación definitiva de personal

Al pulsar Eliminar, la aplicación pide confirmación con el nombre del trabajador y advierte que se borrarán su cuenta y registros personales vinculados. Cancelar no envía la petición. Aceptar ejecuta una única transacción: si falla alguna relación, se revierte toda la limpieza.

Se mantienen los permisos de eliminación existentes y la protección de la cuenta que está en uso. Las cuentas borradas dejan de autenticarse incluso si conservaban una sesión abierta.

## Datos incluidos

- Asistencias, turnos y filas personales de las planillas de asistencia.
- Tareas diarias asignadas y sus evidencias; registros asignados de Cocina, Producción, Mise en place, Limpieza, Incidencias y Control sanitario.
- Vacaciones del trabajador, destinatarios de horarios y Check-in personal anterior con sus tareas y fotografías.
- Cuenta de acceso, preferencias, suscripciones Push, notificaciones, cola de envíos y propuestas de horario.
- Comunicaciones, evaluaciones y reportes diarios vinculados a la cuenta; mensajes del Check-in y resúmenes personales recibidos.
- Entradas de actividad asociadas al nombre de usuario eliminado. Se registra una nueva actividad del administrador que realiza la eliminación.

Los registros de área y planillas compartidas que utiliza el resto del equipo se conservan. Se retira su autoría donde corresponda. Si el trabajador confirmó una tarea de área, se elimina esa evidencia y confirmación, la tarea compartida queda pendiente y se recalculan los totales del resumen afectado. Esta consecuencia aparece en la confirmación.

No se hace una búsqueda y borrado indiscriminado de nombres en textos libres o documentos/imágenes compartidos de otras personas. No se cambia el inventario ni las tareas de otro trabajador. Si existe una nueva relación de una extensión que el servicio no conoce, la base impide la eliminación y la transacción completa se revierte; no se desactivan restricciones para forzarla.

## Aplicación

1. Aplicar `migrations/24_eliminacion_personal.sql` como script completo en Oracle. Este script no borra trabajadores ni registros; permite dejar vacía la referencia al autor de una planilla/horario compartido o del registro de vacaciones de otra persona.
2. Publicar manualmente los cambios de esta entrega mediante commit/push y comprobar Render.
3. Probar primero con un trabajador de prueba: cancelar la advertencia debe conservar todo; aceptar debe quitarlo junto con sus datos personales. Verificar que la cuenta ya no entra y que las otras personas conservan sus registros.

No se ejecutaron eliminaciones en Oracle ni se eligió ningún trabajador real desde el agente. Tampoco se hicieron commit, push o despliegues. No se modificó application-local.properties.

## Archivos

- `backend/src/main/java/cl/restaurante/api/health/EmployeeDeletionService.java`: limpieza explícita por relación y transacción.
- `backend/src/main/java/cl/restaurante/api/health/OracleConnectionController.java`: utiliza el nuevo servicio de eliminación.
- `backend/src/main/resources/static/scrip.js`: advertencia antes de eliminar.
- `backend/src/main/resources/static/index.html` y `service-worker.js`: versión de recursos.
- `backend/src/test/java/cl/restaurante/api/health/EmployeeDeletionIntegrationTest.java`: pruebas con restricciones y base aislada.
- `migrations/24_eliminacion_personal.sql` y esta guía.

## Verificación

Pruebas H2 en modo Oracle, sin credenciales reales: eliminación con cuenta y sin cuenta, conservación de los datos de otras personas, limpieza de evidencias y comprobantes, actualización del resumen, permisos, protección de la cuenta actual y reversión completa ante una relación bloqueante inesperada. También se comprueba el rechazo sin pérdidas si falta la migración de las planillas compartidas. La migración PL/SQL no se ejecutó en Oracle real desde el agente.

Resultado: 18 pruebas seleccionadas aprobadas y empaquetado del JAR correcto. Comprobación sintáctica JavaScript y git diff --check aprobadas.

Comando habitual:

```powershell
mvn -f backend/pom.xml test '-Dtest=EmployeeDeletionIntegrationTest,AreaCheckinIntegrationTest,WebPushEncryptionTest'
```

Mensaje de commit sugerido:

```text
fix: eliminar personal y registros vinculados con confirmacion
```
