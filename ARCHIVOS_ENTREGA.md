# Archivos de esta entrega

| Estado | Archivo | Cambio |
|---|---|---|
| Nuevo | .env.example | Variables nuevas sin secretos. |
| Modificado | backend/pom.xml | Dependencias SMTP y Web Push/cifrado. |
| Nuevo | backend/src/main/java/cl/restaurante/api/health/InventoryAlertService.java | Alertas de mínimo/máximo con deduplicación persistente. |
| Modificado | backend/src/main/java/cl/restaurante/api/health/ModuleController.java | Stock máximo opcional, validación, compatibilidad con clientes anteriores y alertas. |
| Modificado | backend/src/main/java/cl/restaurante/api/health/NotificationController.java | Campana filtra visibilidad interna personal. |
| Nuevo | backend/src/main/java/cl/restaurante/api/health/NotificationDelivery.java | Cola persistente, reintentos y destinatarios. |
| Nuevo | backend/src/main/java/cl/restaurante/api/health/NotificationJobs.java | Activa tareas programadas con opción de deshabilitar en pruebas. |
| Nuevo | backend/src/main/java/cl/restaurante/api/health/NotificationPreferences.java | Preferencias por usuario autenticado. |
| Modificado | backend/src/main/java/cl/restaurante/api/health/NotificationService.java | Origen común de notificaciones y encolado según preferencias. |
| Nuevo | backend/src/main/java/cl/restaurante/api/health/NotificationSettingsController.java | Endpoints personales de preferencias y suscripciones. |
| Nuevo | backend/src/main/java/cl/restaurante/api/health/NotificationTransport.java | Transporte VAPID cifrado y SMTP desde entorno. |
| Modificado | backend/src/main/java/cl/restaurante/api/health/OracleConnectionController.java | Elimina COMMIT manual redundante dentro de la transacción de borrado existente. |
| Nuevo | backend/src/main/java/cl/restaurante/api/health/PushSubscriptions.java | Validación, propiedad y almacenamiento de dispositivos Push. |
| Modificado | backend/src/main/java/cl/restaurante/api/health/TaskEvidenceController.java | Envío propio sin exigir rol administrador; revisión/rechazo y validación de foto. |
| Nuevo | backend/src/main/resources/static/experiencia.js | Cámara/preview, preferencias, vinculación Push y posición de campana. |
| Modificado | backend/src/main/resources/static/gestion.js | Arranque mediante splash y sesión existente. |
| Modificado | backend/src/main/resources/static/index.html | Splash, nueva experiencia, versión de recursos y actualización SW. |
| Modificado | backend/src/main/resources/static/manifest.json | Permite orientación libre y mantiene PWA. |
| Modificado | backend/src/main/resources/static/scrip.js | Acción propia de tarea, rechazo, máximo de stock y preferencias. |
| Modificado | backend/src/main/resources/static/service-worker.js | Caché de archivos reales, exclusión API y recepción Web Push. |
| Modificado | backend/src/main/resources/static/style.css | Resuelve conflicto responsive y clipping de campana; estilos de splash/cámara/preferencias. |
| Modificado | backend/src/test/java/cl/restaurante/api/health/PermissionsIntegrationTest.java | Amplía pruebas aisladas de permisos, tareas, preferencias, cola e inventario. |
| Nuevo | backend/src/test/java/cl/restaurante/api/health/WebPushEncryptionTest.java | Prueba de cifrado y VAPID sin envío externo. |
| Nuevo | backend/src/test/resources/marigex-test-schema.sql | Esquema H2 complementario para pruebas. |
| Nuevo | experiencia.js | Cámara/preview, preferencias, vinculación Push y posición de campana. |
| Modificado | gestion.js | Arranque mediante splash y sesión existente. |
| Modificado | index.html | Splash, nueva experiencia, versión de recursos y actualización SW. |
| Nuevo | INFORME_ENTREGA.md | Informe de cambios, configuración, validación y pendientes. |
| Modificado | manifest.json | Permite orientación libre y mantiene PWA. |
| Nuevo | migrations/05_marigex_push_inventario.sql | Migración Oracle manual aditiva. |
| Nuevo | RESTAURANTE.sql | Consulta diagnóstica conservada desde la copia anidada original. |
| Modificado | scrip.js | Acción propia de tarea, rechazo, máximo de stock y preferencias. |
| Modificado | service-worker.js | Caché de archivos reales, exclusión API y recepción Web Push. |
| Modificado | style.css | Resuelve conflicto responsive y clipping de campana; estilos de splash/cámara/preferencias. |

Los restantes archivos de la raíz original se conservan sin cambios. Las copias anidadas hoy-actualizado no se duplican en la entrega.