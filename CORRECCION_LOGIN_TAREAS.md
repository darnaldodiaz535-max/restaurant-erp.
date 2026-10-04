# Corrección de login y tareas — 2 de octubre de 2026

Esta corrección se aplica sobre la entrega MariGex existente. No cambia Java, Oracle, permisos, imágenes almacenadas, style.css ni responsive.

## Problema confirmado
scrip.js llamaba a bindPushAccount() dentro de enterApplication(), antes de ocultar el login. La función pertenece a experiencia.js. Si ese archivo no se descarga o no se evalúa, el ReferenceError interrumpe la entrada aunque la autenticación haya sido correcta. El botón de tarea también invocaba openTaskCamera() del mismo archivo sin comprobar su disponibilidad.

En el proyecto local experiencia.js existe, pasa la revisión sintáctica y index.html lo incluye en el orden correcto: scrip.js, notificaciones-horarios.js, experiencia.js, gestion.js. Por eso NO se atribuye el fallo a una función inexistente en las fuentes ni a un orden incorrecto confirmado. No se pudo leer la versión publicada por un error TLS en la herramienta; no está confirmado si en producción faltaba el archivo, había una versión mezclada o fallaba su descarga/evaluación. Se comprobó también que target/classes conservaba recursos antiguos: target es salida generada y no es el código fuente que debe publicarse.

## Archivos modificados EXACTOS
En backend/src/main/resources/static/:
- scrip.js: carga compartida y recuperable de experiencia.js usando la misma ruta/base y versión del script principal; Push deja de bloquear el acceso; el botón Realizar tarea espera la dependencia, evita dobles aperturas durante la carga y muestra un error claro si no se recupera.
- gestion.js: comprueba la dependencia antes del splash/arranque. Si falla, arranca la autenticación existente y retira el splash, evitando dejar la aplicación bloqueada.
- index.html: nueva versión en las cuatro referencias JavaScript; se conserva el orden y se incluye experiencia.js. La referencia de style.css no cambia.
- service-worker.js: nueva versión de caché para renovar los recursos de esta corrección; no cambia su lógica de notificaciones ni almacenamiento.

Se sincronizaron los mismos cuatro archivos de la raíz (scrip.js, gestion.js, index.html y service-worker.js), porque el proyecto los conserva duplicados. Son ocho archivos de código en total. experiencia.js fue revisado y se incluye completo, SIN cambios en esta corrección.
Archivo nuevo: CORRECCION_LOGIN_TAREAS.md (este informe).

## Tareas del trabajador
Se conserva el flujo existente: ver módulos/áreas, encontrar la tarea asignada, Realizar tarea, Abrir cámara o Tomar/elegir foto, vista previa, Enviar evidencia, EN_REVISION y aprobación administrativa para COMPLETADA.
No se otorgaron permisos de administración al trabajador. El backend existente comprueba usuario de sesión, empleado responsable, área y estado, y bloquea tareas ajenas y reenvíos de tareas en revisión/completadas. No fue necesario modificarlo.

## Validación de esta corrección
- Sintaxis de los JavaScript modificados: correcta.
- Regresión aislada: el código anterior reproduce bindPushAccount is not defined; el nuevo termina el login incluso con experiencia.js permanentemente ausente.
- Navegador con servidor local y datos ficticios: se simuló un 404 inicial de experiencia.js; la carga de recuperación lo obtuvo; login de trabajador correcto; navegación a Tareas diarias/Cocina y apertura del formulario Realizar tarea correctas.
- style.css comparado byte a byte con el archivo previo: idéntico.
- No se repitieron pruebas Java ni se afirma una nueva compilación Maven; no hay cambios de backend. Las 26 pruebas de la entrega previa quedan documentadas en INFORME_ENTREGA.md.

## Qué probar después
1. Publicar el proyecto completo cuando decidas hacerlo, con experiencia.js dentro de backend/src/main/resources/static/. Evitar subir solamente scrip.js o reutilizar un target/JAR antiguo. Una compilación limpia elimina recursos generados obsoletos.
2. Cerrar y volver a abrir la aplicación/PWA después de actualizar; iniciar sesión como administrador y como trabajador. No debe aparecer el ReferenceError.
3. Crear una tarea PENDIENTE para el empleado vinculado a la cuenta del trabajador y su área. Entrar como ese trabajador, seleccionar su área y comprobar Realizar tarea.
4. En el teléfono, sobre HTTPS, permitir cámara, tomar foto, revisar y enviar. Comprobar EN_REVISION, que no permite enviar otra vez y que el administrador puede ver/aprobar la foto.
5. Entrar con otro trabajador: no debe poder enviar evidencia de esa tarea. Confirmar que COMPLETADA no ofrece reenvío.
6. Si experiencia.js no se puede recuperar por falta de conexión o publicación incompleta, el login sigue disponible; la cámara muestra el error de carga hasta que el archivo esté accesible. Esto no sustituye publicar todos los archivos.

Prueba física de cámara y subida real a Oracle pendiente; no se usaron credenciales ni datos de producción. No se ejecutó SQL ni se hizo commit, push o despliegue. Esta corrección no requiere SQL adicional al ya documentado en la entrega previa.
