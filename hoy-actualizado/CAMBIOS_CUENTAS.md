# Actualización: cuentas individuales desde Personal

Esta actualización sustituye únicamente el flujo de Crear acceso y amplía los permisos de las jefaturas según la nueva solicitud. Se revisaron autenticación, APP_USUARIO, permisos, controladores, frontend, SQL y configuración antes de editar. No se ejecutó SQL contra Oracle, commit, push ni despliegue.

## Uso
Personal → Crear acceso → Usuario, Contraseña y Rol → Crear cuenta.
Usuario: 3–60 caracteres. Contraseña: 10–200 caracteres, campo oculto. Trabajador es la opción inicial. El backend conserva PBKDF2WithHmacSHA256, 180000 iteraciones, sal aleatoria y formato pbkdf2 existente. No se guardan claves en texto plano ni en localStorage.

ADMIN, EMPRESA, JEFE_SALON, JEFE_COCINA y JEFE_LOCAL tienen los mismos permisos administrativos completos. Esto incluye las cuentas de jefatura ya existentes, no solo las creadas después de actualizar. TRABAJADOR/EMPLEADO conservan sus límites. Cada persona mantiene su propio registro APP_USUARIO asociado a APP_EMPLEADO.

## Archivos de código modificados en esta actualización
- index.html y backend/src/main/resources/static/index.html: diálogo con tres campos, selector, validación y cancelación.
- scrip.js y backend/src/main/resources/static/scrip.js: reemplaza los prompts consecutivos, reutiliza POST /api/auth/users, muestra errores, impide doble envío y reconoce roles administrativos en menú/perfil.
- backend/src/main/java/cl/restaurante/api/health/AuthController.java: acepta y valida los roles admitidos incluyendo JEFE_LOCAL; reutiliza inserción y hash existentes. Login/setup/hash no se cambiaron.
- backend/src/main/java/cl/restaurante/api/health/RoleAccessService.java: incorpora JEFE_LOCAL y acceso administrativo completo para las jefaturas.
- backend/src/main/java/cl/restaurante/api/health/ModuleController.java: evita aplicar el filtro exclusivo de Cocina a una cuenta con acceso administrativo completo.
- backend/src/main/java/cl/restaurante/api/health/OracleConnectionController.java: permite a esas cuentas consultar todo Personal, igual que ADMIN.
- backend/src/main/java/cl/restaurante/api/health/TaskEvidenceController.java: permite consultar fotos de todas las áreas con acceso administrativo completo.
- backend/src/test/java/cl/restaurante/api/health/PermissionsIntegrationTest.java: actualiza expectativas de jefaturas, prueba creación/login de todos los roles, hash, rechazo de datos y cuentas duplicadas, y endpoint de creación único.

## Archivos nuevos
- migrations/03_rol_jefe_local.sql
- CAMBIOS_CUENTAS.md (este documento)
Se añadió una nota de actualización en LEEME_CAMBIOS.md, VALIDACION.md y ARCHIVOS_CAMBIADOS.md para distinguir las reglas anteriores de las nuevas.

## Oracle: paso manual necesario
Ejecutar migrations/03_rol_jefe_local.sql como RESTAPI_HOY2609, con F5, antes de crear cuentas JEFE_LOCAL. Amplía solamente el CHECK de ROL. No crea tablas, no agrega columnas y no cambia registros ni contraseñas. Añade CK_APP_USUARIO_ROL_V2 y después elimina únicamente la restricción antigua CK_APP_USUARIO_ROL; no elimina la tabla. Oracle confirma DDL automáticamente. Conservar respaldo. No ejecutar otra vez los scripts originales de creación de tablas.
Si ya aplicaste la migración 01, no necesitas repetirla por este cambio. Si aún no instalaste la versión anterior, sus requisitos de áreas/plazas/horarios siguen vigentes; consulta LEEME_CAMBIOS.md.

## Pruebas manuales
1. Iniciar sesión con el administrador de siempre; abrir Personal.
2. Elegir un empleado sin cuenta y Crear acceso. Comprobar los tres campos y las cinco opciones.
3. Cancelar: no debe crearse cuenta. Reabrir: no debe conservar contraseña.
4. Crear una cuenta por rol, cerrar sesión y entrar con cada usuario/contraseña.
5. Administrador y jefaturas: comprobar Personal, todas las áreas, tareas, fotos y subida/eliminación de horarios.
6. Trabajador: confirmar que conserva sus restricciones y solo ve sus tareas autorizadas.
7. Probar usuario repetido y contraseña corta: no deben crearse cuentas extra.

## Validación
Compilación Java 25 de producción y pruebas correcta. 13 pruebas Spring Boot/MockMvc/H2, sin fallos, errores ni omisiones. Arranque del contexto Spring sin mappings ambiguos y comprobación explícita de un solo POST /api/auth/users. La compilación también comprueba que no hay firmas de métodos duplicadas. JavaScript pasa node --check. Formulario abierto y cancelado en navegador local con datos ficticios.
El comando mvn test estándar encontró el mismo problema local de acceso/lectura de JAR del entorno Windows. Se compilaron las fuentes con el compilador real JDK25 mediante el adaptador local de dependencias y se ejecutó mvn surefire:test. El empaquetado utiliza esas clases ya compiladas/probadas. No se afirma que el ciclo estándar completo mvn test haya pasado en este equipo. La migración Oracle no se ha ejecutado ni probado en Oracle Cloud.
