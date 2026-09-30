# Cambio de contraseña propia

Única funcionalidad añadida: menú de usuario → Cambiar contraseña, disponible para todos los roles con sesión activa.

## Archivos modificados respecto a la versión cuentas-roles
- index.html y backend/src/main/resources/static/index.html: opción del menú y formulario con contraseña actual, nueva y confirmación. Reutiliza estilos existentes.
- scrip.js y backend/src/main/resources/static/scrip.js: validaciones, envío del formulario, errores, mensaje de éxito, cancelación y limpieza de campos. No guarda claves en localStorage.
- backend/src/main/java/cl/restaurante/api/health/AuthController.java: un nuevo POST /api/auth/password y DTO de tres contraseñas sin identificador de usuario. Reutiliza hash() y matches() sin modificarlos. Obtiene el usuario de la sesión activa; compara la clave actual antes de guardar. Actualiza únicamente CLAVE_HASH mediante parámetros SQL y comparación del hash anterior para evitar sobrescrituras concurrentes.
- backend/src/test/java/cl/restaurante/api/health/PermissionsIntegrationTest.java: dos pruebas nuevas que cubren los siete roles existentes, hash, login anterior rechazado/nuevo aceptado, ID ajeno ignorado, rol intacto, validaciones y sesión ausente/inactiva.

Archivo nuevo: CAMBIO_CONTRASENA.md (este documento).

No se cambian roles, creación de cuentas, configuración, esquemas, tablas, columnas ni archivos SQL. Esta mejora NO requiere ejecutar SQL. Las migraciones de versiones anteriores se conservan sin cambios.

## Comportamiento
Nueva contraseña de 10 a 200 caracteres, confirmada y distinta de la anterior. Contraseña actual incorrecta: mensaje claro y ningún cambio. PBKDF2WithHmacSHA256 existente con 180000 iteraciones y sal aleatoria. Éxito: «Contraseña actualizada correctamente». El siguiente login con la contraseña anterior falla y con la nueva funciona. La sesión actual permanece abierta; no se implementó revocación global de sesiones ya abiertas.

## Cómo probar
1. Entrar con una cuenta existente y abrir el menú del usuario → Cambiar contraseña.
2. Probar una contraseña actual incorrecta, nueva corta o confirmación diferente: no debe cambiarse nada.
3. Escribir la actual correcta, una nueva de al menos 10 caracteres y su confirmación.
4. Comprobar el mensaje de éxito; cerrar sesión y verificar que solo permite entrar con la nueva.
5. Repetir con un trabajador y verificar que sus permisos no cambian.
6. Verificar que Personal → Crear acceso sigue mostrando Usuario, Contraseña y Rol.

## Validación y límites
Consultar el resultado informado en la entrega. La suite usa H2 aislado y no accede a Oracle Cloud. El mvn test estándar de este Windows sigue encontrando el problema local de acceso a dependencias JAR; se valida con el compilador real JDK25 mediante el adaptador local, seguido de mvn surefire:test y empaquetado Maven reutilizando esas clases. No se despliega ni se ejecuta SQL en producción.
Validación terminada: 15 pruebas, 0 fallos, 0 errores, 0 omitidas. Compilación Java25 y empaquetado Maven correctos con la alternativa descrita arriba. JavaScript: node --check correcto.
