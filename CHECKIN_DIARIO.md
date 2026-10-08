# Check-in Diario

El módulo es independiente de **Tareas diarias**. Las tareas se leen de `APP_CHECKIN_PLANTILLA`; cuando se comparta la lista definitiva, se insertan ahí como plantillas recurrentes por plaza (`SALON`, `COPERIA`, `COCINA`, `BARRA`). Una plantilla usa `METODO_COMPLETADO='SIMPLE'` para marcar **Hecho** o `FOTO` para exigir evidencia.

## Antes de desplegar

1. Ejecuta `migrations/08_checkin_diario.sql` en Oracle Database Actions con el usuario propietario de las tablas `APP_`.
2. Carga las tareas definitivas en `APP_CHECKIN_PLANTILLA`. La columna `VIGENTE_DESDE` determina desde qué fecha se aplican.
3. Despliega la aplicación. La reconciliación genera el día operativo y los resúmenes semanales de forma idempotente, aunque Render reinicie o despierte tarde.

Los usuarios activos enlazados con `APP_EMPLEADO.ID_EMPLEADO` aparecen en la lista de su plaza. El backend deriva el trabajador autenticado de la sesión y limita los cambios al check-in propio. Solo `ADMIN` y `EMPRESA` tienen acceso a los endpoints de reporte global.

Las fotos se guardan como BLOB y se purgan al finalizar el envío de reportes de su semana; los registros de tareas y estados quedan como historial. `APP_NOTIFICACION.DETALLE` guarda el detalle completo de los resúmenes; el Push usa un texto corto.

## Verificación local

Desde la raíz del proyecto, ejecutar `mvn -f backend/pom.xml test`. El Dockerfile construye con Maven y Java 25.