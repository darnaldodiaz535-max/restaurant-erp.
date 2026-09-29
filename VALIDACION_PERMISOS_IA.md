# Validación de esta entrega

- Compilación Java 25: correcta, 22 fuentes principales y 23 fuentes contando pruebas.
- Maven Surefire: **21 pruebas, 0 fallos, 0 errores, 0 omitidas** sobre H2 aislado. Incluye múltiples peticiones por módulo/rol y cambios de contraseña de los siete roles.
- Maven package: correcto, JAR ejecutable generado a partir de las clases compiladas y los recursos actualizados.
- Los tres JavaScript pasan node --check; las copias frontend raíz/static son idénticas.
- El contexto Spring inicia sin conflictos de endpoints. No se añadieron rutas duplicadas.
- Oracle/configuración, autenticación/hash, Dockerfile, render.yaml y pom.xml conservados.

## Alcance de las pruebas

TRABAJADOR consulta los módulos y áreas, lista personal, plazas, horarios y evidencias. Se rechazan escrituras de negocio, subidas y publicación IA. Las cuentas administrativas conservan sus operaciones. Se comprueban login, cuentas, cambio de contraseña, hash y permisos anteriores.

Notificaciones: aislamiento entre dos trabajadores y administración, contador, marcado propio, rechazo de IDs ajenos, destinatarios de tarea/imagen, reemplazo y eliminación con conservación de destinatarios.

IA: generar no publica, edición antes de publicar, persistencia de borrador, propietario/rol, publicación única, notificaciones dirigidas, rechazo de empleados/horas/fechas inválidos y de solapamientos existentes, error claro sin configuración.

## Limitaciones comprobadas

El mvn test estándar encontrado en este equipo falla al acceder a archivos JAR desde el compilador Maven (error de ruta/acceso del entorno). Se utilizó el compilador real JDK 25 mediante el adaptador local PortableCompile para cargar las mismas dependencias; después mvn surefire:test ejecutó la suite real y mvn package -Dmaven.main.skip=true -Dmaven.test.skip=true empaquetó las clases ya compiladas y probadas. No se presenta esto como un mvn clean test estándar exitoso. No se cambió el pom para ocultar el problema. En tu entorno habitual usa Java 25 y ejecuta mvn test y mvn package.

H2 no implementa TRUNC(SYSDATE,'IW'): en la prueba de lectura completa se sustituye solamente ese agregado de mermas; las demás consultas y comprobaciones de acceso usan la base de pruebas. Esa consulta Oracle existente permanece sin modificar.

El proveedor IA se simula en las pruebas, sin clave ni solicitud facturable. La conexión real queda pendiente de OPENAI_API_KEY y OPENAI_MODEL. SQL preparado para Oracle, no ejecutado ni validado contra tu producción.

El navegador integrado no pudo abrir la pestaña de revisión visual en esta sesión. La revisión visual completa y responsive debe completarse con el checklist de PERMISOS_NOTIFICACIONES_IA.md. No se afirma haber realizado una prueba visual end-to-end de esta entrega.

No se hizo commit, push, despliegue ni cambios en Oracle Cloud.

