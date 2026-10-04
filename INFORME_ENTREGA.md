# Entrega MariGex — 2 de octubre de 2026

## Proyecto entregado
Se trabajó sobre una copia de version final.zip. El original no fue modificado. No se hicieron commits, push, despliegues ni cambios en Oracle Cloud.
El ZIP original contenía tres copias anidadas del proyecto. Se conserva una raíz funcional con Dockerfile y backend/; no se vuelven a empaquetar las copias anidadas redundantes. Se recupera también RESTAURANTE.sql (consulta de diagnóstico, sin modificación de datos). El frontend publicado por Spring Boot está en backend/src/main/resources/static/. Los archivos de frontend de la raíz se sincronizan para evitar versiones contradictorias.
Los documentos anteriores se conservan como historial. Este informe describe esta entrega y tiene prioridad respecto de resultados o pendientes de versiones anteriores.

## Funciones realizadas
- Splash de MariGex antes de resolver login/sesión: mínimo 1,7 segundos; si la red demora, espera la decisión de autenticación para evitar mostrar contenido incorrecto.
- Trabajador: Realizar tarea únicamente si coincide el empleado de la sesión, la asignación y el área. Evidencia guardada en el BLOB existente de Oracle; máximo 4 MB, JPG/PNG/WebP. EN_REVISION y completadas bloquean reenvíos.
- Cámara: solicita cámara trasera con getUserMedia en HTTPS, vista previa y alternativa de archivo/capture. El navegador determina la cámara efectiva. Se liberan los dispositivos al cerrar/ocultar la página.
- Administración: revisión y rechazo mediante el endpoint de revisión existente. Aprobar completa la tarea; rechazar vuelve a pendiente y conserva la evidencia/resultado. Se impide revisar una evidencia antigua cuando existe una posterior.
- Notificaciones: se conserva APP_NOTIFICACION como origen común. Destinatarios personales y administrativos existentes; campana propia y lectura propia. Preferencias internas, Push y correo por usuario en Configuración.
- Web Push real: cifrado AES128GCM, firma VAPID, suscripciones por usuario/dispositivo, colas persistentes, reintentos limitados, limpieza de suscripciones 404/410 y desvinculación al cerrar sesión. Nunca se envía la clave privada al frontend. Se aceptan servicios Push de Chrome/Firefox/Apple/Windows; otros proveedores requieren adaptar la lista permitida.
- Correo opcional: Spring Mail/SMTP, destinatario de preferencias, sin credenciales en código. El correo indicado por el usuario no tiene verificación de titularidad; no se añadió registro ni recuperación por correo.
- Inventario: stock máximo opcional; alerta cuando stock <= mínimo o stock > máximo. Estado persistente evita repetir avisos mientras permanece el mismo estado; regresar a normal permite una alerta posterior. Comprobación inmediata en edición y periódica cada cinco minutos para cambios externos.
- Responsive: reglas de navegación separadas de tarjetas/formularios. La regla móvil general de 620px imponía navegación horizontal y podía contradecir landscape. Ahora esa navegación aplica solo a portrait; la regla de 900px precede la específica de teléfono landscape, que aplica con puntero táctil, altura <=500px y ancho <=1000px. Tablet/escritorio mantienen sus anchos originales. El panel de campana se coloca fuera de topbar y se limita al viewport.
- Service worker: nombres reales (scrip.js), actualización de caché, solo recursos estáticos; no guarda sesiones, API ni fotos privadas. La PWA puede rotar y conserva iconos. Sin internet no ofrece edición ni autenticación offline.
- Se mantienen autenticación/hash, cambio de contraseña, roles, módulos, horarios, fotos y configuración de conexión. No se añadió IA ni API de IA; el código preexistente de propuestas sigue conservado.

## Oracle: ejecución MANUAL pendiente
Ejecutar migrations/05_marigex_push_inventario.sql conectado como RESTAPI_HOY2609, usando Ejecutar script. El SQL exacto está íntegro en ese archivo; no fue ejecutado en producción.
Requiere el esquema actual con las migraciones previas 01, 03 y 04 aplicadas; 02 es verificación. No vuelva a ejecutar scripts iniciales de creación sobre producción. Si falta una migración anterior, revisar su contenido antes de aplicarla.

Nuevas columnas:
| Tabla | Columna | Tipo / valor inicial | Finalidad |
|---|---|---|---|
| APP_INSUMO | STOCK_MAXIMO | NUMBER(12,3), nullable | Límite superior opcional |
| APP_INSUMO | ALERTA_ESTADO | VARCHAR2(10 CHAR), NORMAL, NOT NULL | Deduplicación NORMAL/BAJO/ALTO |
| APP_NOTIFICACION | VISIBLE | CHAR(1), S, NOT NULL | Preferencia interna para nuevos avisos |
| APP_EVIDENCIA_TAREA | RESULTADO | VARCHAR2(12 CHAR), nullable | APROBADA/RECHAZADA; evidencias antiguas conservan NULL |

Nuevas tablas:
- APP_NOTIF_PREFERENCIA: ID_USUARIO NUMBER PK/FK; INTERNA/PUSH/CORREO CHAR(1), EMAIL VARCHAR2(254 CHAR). Defaults S/N/N; checks S/N.
- APP_PUSH_SUSCRIPCION: ID_SUSCRIPCION NUMBER identity PK, ID_USUARIO NUMBER FK, ENDPOINT VARCHAR2(2000 CHAR), ENDPOINT_HASH VARCHAR2(64 CHAR) único, P256DH VARCHAR2(100 CHAR), AUTH VARCHAR2(40 CHAR), ACTUALIZADO_EN TIMESTAMP. Hasta 10 dispositivos por usuario, validado en backend.
- APP_NOTIF_ENVIO: ID_ENVIO NUMBER identity PK, ID_NOTIFICACION NUMBER FK, CANAL VARCHAR2(10 CHAR), DESTINO VARCHAR2(254 CHAR), ESTADO VARCHAR2(12 CHAR), INTENTOS NUMBER, PROXIMO_INTENTO TIMESTAMP, ULTIMO_ERROR VARCHAR2(100 CHAR). Unicidad por notificación/canal/destino; checks de canal/estado.
- Checks de stock máximo >= mínimo y >=0, estado de alerta, visibilidad y resultado. Índices de envíos pendientes y suscripciones por usuario. Las FK nuevas tienen ON DELETE CASCADE para limpiar dependencias si posteriormente se elimina su padre.

La migración NO contiene DROP, TRUNCATE ni eliminación de filas. Añade estructura y valores predeterminados a columnas nuevas; no cambia contraseñas, usuarios, roles ni stocks existentes. Oracle confirma DDL automáticamente. Las comprobaciones de existencia permiten reejecución, pero no reparan una estructura previa incompatible. Conservar respaldo antes de migrar. Al arrancar, los artículos ya bajos pueden generar una primera alerta. Aplicar esta migración antes de arrancar el código nuevo, porque las consultas utilizan las nuevas columnas.

## Configuración externa
.env.example contiene únicamente nombres y valores de ejemplo, sin secretos. Spring lee variables del proceso; no carga automáticamente ese archivo. En Render se agregan manualmente en Environment, conservando intactas las variables Oracle actuales.

Push:
- MARIGEX_VAPID_PUBLIC_KEY y MARIGEX_VAPID_PRIVATE_KEY: par ECDSA P-256 VAPID, codificado base64url; pública de 65 bytes (punto no comprimido), privada de 32 bytes. Generar localmente con una herramienta VAPID confiable; mantener el par estable y la privada fuera de GitHub.
- MARIGEX_VAPID_SUBJECT: contacto mailto: real (o URL HTTPS de contacto). El ejemplo debe reemplazarse.
- Tras configurar y arrancar en HTTPS, cada usuario entra a Configuración y pulsa Activar push en este dispositivo. Se requiere gesto y permiso del usuario. Desmarcar Push suspende todos sus dispositivos; desactivar el dispositivo elimina solo esa suscripción.

Correo:
- MARIGEX_MAIL_ENABLED=true habilita el transporte cuando hay HOST y FROM.
- MARIGEX_SMTP_HOST/PORT: servidor y puerto; 587 predeterminado.
- MARIGEX_SMTP_USERNAME/PASSWORD: credenciales del proveedor, solo entorno seguro.
- MARIGEX_SMTP_STARTTLS=true para STARTTLS (587 normalmente).
- MARIGEX_SMTP_SSL=true y STARTTLS=false si el proveedor exige TLS directo (465 normalmente).
- MARIGEX_MAIL_FROM: remitente autorizado por el proveedor.
- El usuario debe indicar su correo y activar su preferencia. Configurar DNS/autorización de remitente conforme a su proveedor.

La cola intenta entregas cada 30s, hasta cinco fallos, con espera creciente. Un canal sin configurar deja sus envíos pendientes; al configurarlo puede enviar avisos acumulados. Se vuelven a comprobar preferencias/destinatario al entregar. La entrega es al menos una vez: un reinicio en el instante posterior al envío puede producir duplicados, especialmente de correo. No se promete entrega inmediata ni que el sistema operativo siempre muestre avisos.

## Validación YA realizada
- Compilación de Java 25: correcta, 29 fuentes principales y las fuentes de prueba.
- Maven Surefire: 26 pruebas, 0 fallos, 0 errores, 0 omitidas. La última ejecución ya estaba iniciada antes de la instrucción de detener pruebas; se recogió su resultado sin volver a ejecutarla.
- 25 pruebas de integración H2 aislado: permisos/visibilidad de trabajador, sesiones, cambio de contraseña y cuentas existentes, notificaciones personales, flujo tarea-foto-rechazo-reenvío-aprobación, intento de otro empleado rechazado, alertas de inventario y deduplicación, cola/reintentos/suscripciones vencidas.
- 1 prueba criptográfica: generación y cifrado real del payload y firma VAPID, sin conexión a un servicio Push.
- Comprobación local del service worker: recursos existentes, API sin caché, Push de usuario propio, descarte de otro usuario y desvinculación.
- Navegador con API ficticia local: portrait 390x844, landscape 844x390, tablet 768x1024, escritorio 1440x900. Navegación y campana visibles; trabajador con Realizar tarea, administración deshabilitada; formulario de cámara y preferencias presentes. Para landscape se emuló el media feature pointer:coarse solo en la fixture de prueba, sin cambiar la condición del CSS entregado.

Limitación de compilación local: Maven compiler encontró un problema de resolución de rutas ZIP/JAR dentro del entorno Windows restringido. Se utilizó el compilador Java 25 real con un adaptador local de lectura de dependencias, y luego Maven Surefire sobre las clases compiladas. Por tanto, no se afirma que mvn package estándar se haya completado ni se entrega un JAR de producción. El adaptador y el caché local no forman parte del proyecto. En un entorno normal con JDK25/Maven, el comando de validación es mvn -f backend/pom.xml test o package. No se iniciaron nuevas pruebas ni empaquetado Maven después de la orden final del usuario.

## Pendientes físicos / externos
- Android e iPhone reales: cámara trasera, permisos, captura, archivo alternativo, vista previa, subida, rotación, cierre de cámara y accesibilidad con teclado virtual.
- Instalar PWA y probar arranque, sesión existente, login, actualización tras una versión previa y modo sin conexión.
- iPhone/iPad: Web Push requiere iOS/iPadOS 16.4+ y abrir la aplicación instalada en pantalla de inicio; solicitar permiso desde un gesto. Probar aviso con aplicación en segundo plano, pulsación, logout y cambio de cuenta.
- Push extremo a extremo con claves VAPID propias y suscripciones reales; SMTP con proveedor real. No se enviaron correos ni avisos a dispositivos personales.
- Migración y recorrido funcional en Oracle de ensayo; no se probó contra Oracle Cloud ni se utilizaron sus credenciales. H2 no sustituye la validación de DDL/driver Oracle.
- Compilación Maven estándar y despliegue quedan a cargo del usuario; no se desplegó.

Referencias de plataforma: https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/ y https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia .

## Comprobaciones manuales sugeridas para cuando decidas probar
1. Aplicar solo migración 05 en una copia Oracle preparada; iniciar con variables actuales.
2. Administrador crea tarea con responsable/área; trabajador ve campana, entra al área, envía foto; otro trabajador no puede enviarla; administrador rechaza y luego aprueba una nueva evidencia.
3. Confirmar trabajador ve módulos y no puede editar administrativamente ni usando API directa; mantiene cambio de contraseña propio.
4. Stock normal sin alerta, igual al mínimo con alerta, repetición sin duplicación, normal y nuevo cruce con nueva alerta; superar máximo con sobrestock.
5. Dos usuarios con preferencias distintas reciben solo sus avisos. Activar Push en teléfono y correo propio; desactivar y comprobar que no llegan nuevos envíos por ese canal.
6. Revisar horarios/fotos, Personal, Asistencia, incidencias, sesiones y módulos existentes antes de publicar.

## Archivos modificados y nuevos
Ver ARCHIVOS_ENTREGA.md: inventario exacto comparado con la copia raíz del ZIP original. No se incluyen .git, target, cachés, dependencias, credenciales reales ni archivos temporales.
