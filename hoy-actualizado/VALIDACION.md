# Validación realizada

- Compilación real con **Java 25**: 18 fuentes de producción, sin errores.
- Pruebas Spring Boot / MockMvc / H2: **11 ejecutadas, 0 fallos, 0 errores, 0 omitidas**.
- Empaquetado Maven + Spring Boot `repackage`: **BUILD SUCCESS**, JAR ejecutable generado.
- `node --check` en `scrip.js` y `gestion.js`: correcto.
- Revisión de navegador local con datos ficticios: áreas, formulario de tareas, filtro de responsables, gestión de plazas, menús por rol y botones administrativos de horarios deshabilitados para trabajador.
- Revisión de las tarjetas de áreas a 1280×800 y 390×844: sin desbordamiento horizontal de la página. Las tablas usan desplazamiento interno.
- Comparación de las dos copias del frontend y de los archivos de conexión/despliegue contra el ZIP original.

## Casos automáticos

1. Sin sesión, endpoints protegidos devuelven 401.
2. Trabajadores y jefaturas operativas no pueden administrar personal, plazas ni horarios por solicitudes directas.
3. El trabajador solo consulta tareas propias del área autorizada; asignación a otra área rechazada y Cocina no modifica tareas de Salón.
4. Intento de sobrescribir asistencia ajena rechazado.
5. Evidencia exige tarea propia pendiente; evita segundo envío; aprobación conserva la foto y genera actividad.
6. Horario almacenado en BLOB, leído por trabajador, reemplazado sin duplicación y eliminado por administrador.
7. Archivo falso o mayor de 4 MB rechazado sin insertar horario.
8. Plaza calcula ocupación a partir de CARGO y AREA de empleados activos, normalizando acentos.
9. Incidencia de trabajador fuerza identidad, área y estado inicial propios; el aviso aparece para el administrador.
10. Inventario conserva creación/consulta para administrador y rechaza escritura de trabajador.
11. Cuenta desactivada pierde acceso aunque tenga una sesión anterior.

## Particularidad del entorno local

El compilador Maven encontró un error de Windows al resolver/cerrar rutas canónicas de archivos JAR (`AccessDeniedException`), aunque podía leerlos. Para validar no se cambió el POM de producción: se utilizó el compilador JDK 25 con un adaptador de lectura de dependencias en memoria. Luego se ejecutó `mvn surefire:test` sobre las clases compiladas. El empaquetado Maven se ejecutó con `-Dmaven.main.skip=true -Dmaven.test.skip=true` reutilizando esas clases ya compiladas y probadas, sin afirmar que un `clean verify` completo terminó en este entorno.

La descarga Maven necesitó que su almacén temporal de confianza incluyera el certificado de AVG que ya confía Windows. No se desactivó TLS ni se modificó el almacén global de Java. Estos archivos auxiliares no forman parte del proyecto entregado.

En un entorno normal de desarrollo/Render, se mantiene el build estándar del proyecto. Se agregaron únicamente las dependencias de prueba (scope `test`).

## Pendiente en el entorno del restaurante

No se ejecutó la migración en un motor Oracle ni se conectó con Oracle Cloud. H2 valida los flujos Java, pero no sustituye la prueba del SQL Oracle, sus privilegios/cuota, la migración de los datos reales ni una prueba real de reinicio contra esa base. Tampoco se desplegó en Render. Ejecuta las comprobaciones manuales de `LEEME_CAMBIOS.md` antes de publicar.

## Validación de la actualización de cuentas
La suite actual contiene 13 pruebas, incluyendo creación y login por rol, almacenamiento PBKDF2, permisos completos de las jefaturas, trabajadores limitados y un único POST /api/auth/users. Las expectativas antiguas que impedían administración a las jefaturas fueron sustituidas conforme a la nueva solicitud. Ver CAMBIOS_CUENTAS.md para los archivos y detalles de compilación de esta versión.
