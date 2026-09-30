# Actualización del proyecto existente

Se trabajó sobre `hoy.zip`, conservando la aplicación HTML/CSS/JavaScript y Java 25 / Spring Boot 4.1.1. El análisis previo está en `ANALISIS.md`. No se publicó en GitHub/Render ni se ejecutó SQL en Oracle Cloud.

## Qué cambió

- **Personal:** conserva sus estadísticas, fichas y formulario actual. Se agrega **Agregar plaza / Gestionar plazas**, exclusivo de EMPRESA/ADMIN. Una plaza define cargo, área, cupos y estado; su ocupación se calcula con empleados activos del mismo cargo y área. Desactivar una plaza no elimina empleados. Los cargos se comparan ignorando mayúsculas y espacios exteriores; utiliza el mismo nombre de cargo en Personal.
- **Áreas:** Cocina, Salón, Barra y Copería tienen entradas independientes con el equipo correspondiente y acceso a sus tareas. Cocina mantiene sus preparaciones existentes mediante **Preparaciones de cocina**.
- **Horarios:** es el nuevo nombre visual de Turnos. Se conservan APP_TURNO y `/api/modulos/turnos`. Se agregan imágenes JPG/PNG/WebP de hasta **4 MB**, con título, fecha y autor, almacenadas en un **BLOB de Oracle**, sin archivos en el disco efímero de Render. Reemplazar actualiza el horario; eliminar borra solamente el horario seleccionado.
- **Tareas diarias:** la entrada muestra cuatro tarjetas. Cada tarjeta carga solo su área. Los trabajadores ven exclusivamente sus tareas asignadas y envían la foto con el flujo existente: **PENDIENTE → EN_REVISION → COMPLETADA**. La jefatura puede aprobar conservando la imagen. Se mantiene la opción anterior de borrar evidencia después de revisarla. Una evidencia antigua no puede aprobar una tarea reasignada.
- **Notificaciones:** la campana y el Dashboard reutilizan APP_ACTIVIDAD para mostrar envíos/aprobaciones de tareas e incidencias reportadas, además de las alertas existentes. Se consultan cada 60 segundos mientras la página esté visible. Son avisos dentro de la aplicación; no correo, notificaciones push ni un sistema de leído/no leído.
- **Mi perfil:** conserva Nombre, Cargo, Área, Usuario y Tipo de acceso.
- **Otros módulos:** se mantienen sus tablas, campos y endpoints. Se cierra un acceso indebido que permitía intentar modificar por ID registros de otro trabajador/área. También se corrige el formato de fechas Oracle al editar formularios y se devuelven errores de validación comprensibles.

## Permisos

Se reutiliza APP_USUARIO.ROL, la sesión HTTP y las contraseñas PBKDF2 existentes. No se crean roles nuevos ni se renombran los actuales.

| Función | EMPRESA / ADMIN | JEFE_SALON | JEFE_COCINA | TRABAJADOR / EMPLEADO |
|---|---|---|---|---|
| Gestionar personal, accesos, plazas y configuración | Sí | No | No | No |
| Consultar Personal | Sí | Sí | Equipo de Cocina | No |
| Crear/reemplazar/eliminar horarios | Sí | No | No | No |
| Consultar imágenes de horarios | Sí | Sí | Sí | Sí |
| Gestionar tareas | Todas las áreas | Todas las áreas | Cocina | No |
| Ver tareas | Todas | Todas | Cocina | Solo propias y de su área |
| Enviar foto de una tarea | Flujo de revisión | Flujo de revisión | Flujo de revisión | Solo la tarea propia pendiente |
| Aprobar evidencias | Sí | Sí | Cocina | No |
| Actividad general y avisos | Sí | Sí | No | No |

Las jefaturas conservan sus permisos operativos anteriores en los demás módulos. El trabajador conserva asistencia y producción propias y puede reportar incidencias propias. El backend impone estas restricciones aunque se manipule el navegador. La imagen del horario es consultable por todo usuario autenticado; no se limita al área.

## Oracle: qué ejecutar manualmente

**No vuelvas a ejecutar APP_ESQUEMA.sql, APP_USUARIO.sql ni APP_PERMISOS_Y_EVIDENCIAS.sql sobre tu instalación actual.** Esos archivos originales se conservan como referencia.

1. Conserva un respaldo de la base y del código publicado. Prueba primero en una copia de la base cuando sea posible.
2. Abre SQL en **Oracle Database Actions de Cloud**, con el usuario **RESTAPI_HOY2609** de esa base. No es SYSTEM en localhost ni el usuario de inicio de sesión de la aplicación web.
3. Abre `migrations/01_areas_plazas_horarios.sql` y ejecútalo como **script (F5)**. Si utilizas SQL Developer, la conexión debe apuntar a Oracle Cloud con RESTAPI_HOY2609. El script comprueba el usuario y las tablas originales antes de hacer cambios.
4. Confirma el mensaje **Migración terminada** y ejecuta `migrations/02_verificacion.sql`, que es de solo lectura. Si hay un error, conserva el mensaje y detente; no intentes borrar tablas para resolverlo.
5. Las tablas APP_PLAZA y APP_HORARIO_IMAGEN deben aparecer. APP_TAREA_DIARIA debe tener AREA.

La migración agrega AREA y su índice, y crea las dos tablas nuevas. Clasifica tareas sin área a partir del AREA del responsable cuando coincide con Cocina, Salón, Barra o Copería, ignorando acentos/mayúsculas. No cambia las fichas de empleados ni las credenciales. Las tareas que no se puedan clasificar quedan para el administrador en **Clasificar tareas antiguas sin área**. Allí se edita el área y se asigna un responsable compatible.

El script admite repetición tras una ejecución correcta o parcial: comprueba los objetos antes de crearlos. Oracle confirma DDL automáticamente; no existe rollback global del script. Si ya tienes tablas homónimas de otra modificación, compara su estructura antes de ejecutar.

Las imágenes consumen almacenamiento de Oracle. Se mantienen mientras no se eliminen desde la aplicación o se pierda la base; deben incluirse en los respaldos de Oracle. La autenticación ORDS/web de Oracle no necesita cambios para servir las imágenes a través de Spring Boot.

## Comprobar antes de GitHub / Render

Con Java 25 y Maven instalados, desde la carpeta del proyecto:

```powershell
mvn -f backend/pom.xml clean verify
node --check scrip.js
node --check gestion.js
```

Las pruebas usan **H2 en memoria**, con datos ficticios y perfil `test`; no usan Oracle ni requieren sus credenciales. Las operaciones de limpieza de las pruebas afectan únicamente esa base efímera. El build de producción no incorpora H2 ni las dependencias de test.

Para la prueba completa con Oracle, utiliza una copia de tu base con el esquema RESTAPI_HOY2609, aplica la migración allí y proporciona localmente las variables que el proyecto ya utiliza: ORACLE_DB_USER, ORACLE_DB_PASSWORD, ORACLE_JDBC_URL y SPRING_PROFILES_ACTIVE=oracle. No escribas contraseñas en archivos que vayas a subir a GitHub. Inicia:

```powershell
mvn -f backend/pom.xml spring-boot:run
```

Abre `http://localhost:8080` (o el puerto PORT que ya tengas definido). Inicia sesión con las cuentas de **la aplicación**, no con RESTAPI_HOY2609. No abras index.html directamente como archivo: las funciones necesitan Spring Boot.

Prueba con una cuenta EMPRESA/ADMIN y otra TRABAJADOR/EMPLEADO, preferiblemente en navegadores o sesiones separadas:

1. **Regresión:** entra/sal, revisa Mi perfil y los módulos que ya utilizabas. Compara registros y estadísticas de Personal; no deben perderse datos.
2. **Plazas:** crea una plaza con un cargo existente y un área; comprueba cupos y ocupados. Edita cupos y desactívala. El trabajador no debe acceder a esta gestión.
3. **Áreas:** entra en las cuatro como administrador. El trabajador debe tener acceso a la propia. Cocina debe seguir mostrando el botón de preparaciones para las jefaturas autorizadas.
4. **Tareas:** crea una tarea en cada área con responsable compatible. El trabajador debe ver solo las suyas; otra área debe estar bloqueada. Envía una foto, verifica EN_REVISION y el aviso al administrador. Aprueba y comprueba COMPLETADA y que la foto sigue visible. Comprueba también las tareas antiguas sin área.
5. **Horarios:** sube una imagen, cierra sesión y vuelve a entrar; debe seguir visible. Reinicia el backend usando la misma base y comprueba que permanece. Reemplaza y elimina una imagen de prueba. Con trabajador, los botones deben estar deshabilitados; los endpoints de escritura deben responder 403.
6. **Incidencias:** reporta como trabajador. Debe quedar asociada a su identidad/área y verse en la campana del administrador en hasta 60 segundos, o al actualizar el Dashboard.
7. **Responsive:** revisa a 390 px y en computadora. Las tablas extensas se desplazan dentro de su contenedor.
8. **Módulos conservados:** comprueba un registro de prueba de inventario, producción, mise en place, reservas, limpieza, compras, mermas, control sanitario y reportes en la base de prueba antes de publicar.

## Publicación posterior, a tu cargo

No hay despliegue automático hecho por esta entrega. Una vez que apruebes la prueba, aplica la migración en la base de producción antes de publicar este backend/frontend. La migración es aditiva y el código anterior puede seguir usando sus columnas originales. Copia los archivos del ZIP sobre el repositorio correcto, revisa el diff y publica cuando corresponda. Si Render tiene auto-deploy al hacer push, ese push iniciará el despliegue.

Dockerfile, render.yaml y los dos application*.properties permanecen idénticos al ZIP recibido. No necesitas cambiar la conexión, contraseña ni perfil en Render. El frontend raíz y la copia en `backend/src/main/resources/static` están sincronizados; conserva ambos al subir.

## Archivos

El detalle completo de nuevos/modificados está en `ARCHIVOS_CAMBIADOS.md`. Los resultados y límites de validación están en `VALIDACION.md`.

## Actualización posterior: cuentas desde Personal
Las reglas de jefaturas documentadas arriba pertenecen a la versión anterior. En la actualización actual ADMIN, EMPRESA, JEFE_SALON, JEFE_COCINA y JEFE_LOCAL tienen acceso administrativo completo. Se incorpora el formulario Usuario/Contraseña/Rol. Consultar CAMBIOS_CUENTAS.md y ejecutar manualmente migrations/03_rol_jefe_local.sql para admitir JEFE_LOCAL. No se modifican credenciales ni datos existentes mediante esa migración.
