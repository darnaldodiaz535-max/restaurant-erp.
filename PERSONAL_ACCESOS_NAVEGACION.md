# Personal, accesos y navegación móvil

## Diagnóstico y cambios

El cargo APP_EMPLEADO.CARGO, el área APP_EMPLEADO.AREA y el rol APP_USUARIO.ROL son datos diferentes. Login verifica usuario, hash y cuenta activa; cambiar el cargo visible no debe ser necesario. No se ha comprobado el caso concreto de producción. Se normalizan aliases explícitos como «Jefe de Salón» y «Encargado de Salón» a JEFE_SALON tanto al crear accesos como al leer la sesión. Los nombres desconocidos se rechazan al crear accesos; jamás se deducen privilegios del cargo o del área de la ficha. Las cuentas deben seguir creándose desde Crear acceso con su rol seleccionado. Los permisos, hashes y contraseñas actuales se conservan.

Personal expone accessRole aparte del cargo y el formulario aclara la diferencia. No se ejecutó ninguna actualización masiva de roles ni se modificaron credenciales.

Solo había un listener DELETE, pero no existía bloqueo de clics. Se añaden bloqueo por trabajador y botones deshabilitados. El endpoint mantiene autenticación/autorización y responde 204 si el trabajador ya no existe (reintento idempotente). La transacción existente de eliminación y su alcance confirmado no cambian. Se conservan sus comprobaciones de relaciones y rollback. No se ejecutaron borrados contra Oracle. La eliminación definitiva existente continúa eliminando los datos personales indicados en su confirmación; no se cambió a archivado.

Al confirmar DELETE se retira inmediatamente la fila y se recalculan los tres contadores desde la lista local. Se invalidan respuestas de consultas previas que podrían volver a mostrarla. Un fallo posterior del Dashboard no se anuncia como fallo de eliminación. No se modifica el flujo de creación de trabajadores.

## Actualización y navegación

app-navigation.js coordina el botón del título, Dashboard y Pull to Refresh. Fusiona solicitudes de actualización simultáneas, conserva posición, filtros, detalles abiertos y formularios. Los módulos conservan sus cargadores específicos; no se reinicia la aplicación ni se reemplazan los formularios por recargas completas. Los fallos conservan los datos anteriores y muestran un aviso visible. Personal y Dashboard evitan vaciar valores durante la consulta; los cargadores de listas y horarios también conservan el contenido en errores de red.

El gesto comienza únicamente en la parte superior del documento, con un dedo y un arrastre predominantemente vertical suficientemente largo. Se excluyen controles, diálogos y contenedores con desplazamiento horizontal. Un indicador muestra «Suelta para actualizar» y «Actualizando…». El gesto se cancela al cambiar de pantalla, desplazar hacia arriba, usar varios dedos o deslizar horizontalmente. No sustituye la confirmación de formularios ni sube fotos pendientes.

El nuevo acceso Dashboard está en una franja compacta sticky que ocupa espacio normal en el documento. Considera safe-area y ajusta el desplazamiento del menú lateral a la altura real de la franja. Al tocarlo, o al tocar Dashboard en el menú, se consultan los datos y se vuelve suavemente al inicio. El movimiento respeta prefers-reduced-motion. El título RESTAURANT ERP actualiza el apartado actual conservando la posición.

Se versionaron los recursos y el Service Worker incluye app-navigation.js para las instalaciones móviles.

## Archivos

- backend/src/main/java/cl/restaurante/api/health/AuthController.java
- backend/src/main/java/cl/restaurante/api/health/RoleAccessService.java
- backend/src/main/java/cl/restaurante/api/health/OracleConnectionController.java
- backend/src/main/resources/static/app-navigation.js
- backend/src/main/resources/static/scrip.js
- backend/src/main/resources/static/gestion.js
- backend/src/main/resources/static/index.html
- backend/src/main/resources/static/style.css
- backend/src/main/resources/static/service-worker.js
- backend/src/test/java/cl/restaurante/api/health/PermissionsIntegrationTest.java
- backend/src/test/java/cl/restaurante/api/health/EmployeeDeletionIntegrationTest.java
- backend/src/test/frontend/navigation.test.cjs
- backend/src/test/frontend/preview_personal.py
- PERSONAL_ACCESOS_NAVEGACION.md

## Verificación

27 pruebas seleccionadas de Spring/H2 y empaquetado Maven: correctos. Incluyen roles de Salón, aislamiento de permisos, contraseña mínima, borrado repetido, protección del usuario actual y rollback en relaciones desconocidas, además de regresión de limpieza semanal y cifrado Push. Las pruebas H2 utilizan bases efímeras separadas, sin credenciales de Oracle. No se afirma que toda la suite histórica esté verde: PermissionsIntegrationTest tenía casos ajenos con fallos anteriores; se ejecutaron sus cuatro casos pertinentes.

Pruebas frontend con Node: `node --test backend/src/test/frontend/navigation.test.cjs`. Comprueban actualización única, errores sin pérdida de controles, gesto al inicio y exclusiones, navegación y eliminación con doble clic.

Vista previa aislada: `python backend/src/test/frontend/preview_personal.py`; abrir http://127.0.0.1:8766. Contiene únicamente personas ficticias en memoria; reiniciarla restaura sus datos. Ctrl+C detiene el servidor. No usar para probar persistencia de producción. Se verificó en navegador a 1280 px y 390 px: acceso sticky visible tras desplazamiento, Dashboard final en scrollY=0, búsqueda de Personal conservada tras actualizar y sin desbordamiento horizontal de página móvil.

La herramienta del navegador se bloqueó al abrir la confirmación nativa de eliminación; el flujo de doble clic y retirada inmediata está verificado automáticamente con el manejador real y el backend, pero no se certifica ese modal de extremo a extremo. El gesto se verificó con eventos táctiles simulados; falta probarlo en un iPhone/Android físico y la versión instalada. No se comprobó producción ni se desplegó.

## Publicación manual

No requiere nuevas migraciones SQL. No se ejecutó git add, commit, push ni despliegue. No incluir backend/src/main/resources/application-local.properties: es configuración local preexistente.

Commit sugerido: fix: corregir accesos y eliminación de personal y mejorar navegación móvil

Tras hacer push desde VS Code, en Render abrir el servicio restaurant-erp y revisar Events/Logs. El despliegue debe corresponder al nuevo commit y terminar Live. Si Auto-Deploy está deshabilitado, ejecutar Manual Deploy > Deploy latest commit. No usar Clear build cache salvo que un fallo concreto lo justifique.

En la web y aplicación instalada aceptar Actualizar si aparece. Comprobar: una cuenta de prueba con cargo Jefe de Salón y rol de acceso JEFE_SALON inicia sesión; Personal actualiza contadores y conserva la búsqueda; el botón del título mantiene el apartado; Dashboard vuelve arriba incluso desde su propia pantalla; el gesto consulta datos sin perder formularios. No eliminar trabajadores reales como prueba de despliegue. Comprobar una pérdida temporal de conexión: el aviso aparece y la lista sigue visible.


## Ajuste visual posterior

Por solicitud del usuario se retiraron el botón Dashboard duplicado y su franja blanca. Se mantiene Dashboard en el menú original y RESTAURANT ERP para actualizar el apartado actual. Los avisos de actualización ahora son temporales y flotantes; no reservan una franja vacía. Se conserva Pull to Refresh. Esta corrección sustituye la descripción anterior del acceso superior adicional.
