# Sistema de Gestión - Restaurante

## Preparar la base de datos para los accesos

1. Abre SQL Developer y conecta con `RESTAURANTE_APP`.
2. Abre el archivo `APP_USUARIO.sql` que está en la carpeta principal del proyecto.
3. Ejecuta el `CREATE TABLE` con **Ctrl+Enter**. Debe aparecer “Table created”. Este paso se hace una sola vez.
4. Repite lo mismo con `APP_ACTIVIDAD.sql`, también una sola vez. Esta tabla permite mostrar el historial real de acciones.

## Iniciar la aplicación

En VS Code, abre una terminal PowerShell en la carpeta `backend`. Si hay una ejecución anterior, detenla con **Ctrl+C**. Después inicia la aplicación:

```powershell
mvn -Poracle spring-boot:run
```

Deja esa terminal abierta. En Edge visita `http://localhost:8080/` (no abras `index.html` como archivo).

La primera vez aparecerá el formulario para crear el jefe de empresa. Selecciona la ficha de Personal de quien administrará el sistema y crea su usuario y contraseña. Luego podrá crear los accesos del equipo desde Personal.

## Funciones conectadas

- Personal y los módulos operativos leen y guardan datos en Oracle.
- Los indicadores del Dashboard se calculan desde las tablas al abrir o volver al Dashboard.
- El perfil muestra los datos del trabajador asociado a la sesión.
- Cerrar sesión invalida la sesión del servidor.
- Configuración permite agregar, editar y eliminar opciones guardadas en `APP_CONFIGURACION`.

## Roles y fotos de tareas

Después de la instalación inicial, ejecuta una sola vez `APP_PERMISOS_Y_EVIDENCIAS.sql` en SQL Developer conectado como `RESTAURANTE_APP`. El script habilita los perfiles Jefe de empresa, Jefe de salón, Jefe de cocina y Trabajador, y crea la tabla que guarda las fotos de las tareas en Oracle.

El jefe de empresa puede crear accesos para el equipo desde Personal y elegir el perfil correspondiente. El jefe de salón puede revisar y borrar fotos desde Tareas diarias; cocina administra sus módulos; el trabajador ve sus propias tareas asignadas y puede completarlas adjuntando una foto JPG, PNG o WebP de hasta 5 MB. La foto queda almacenada en la base de datos.

Si VS Code pregunta si debe sincronizar la configuración/classpath de Java después de cambiar `pom.xml`, acepta la sincronización.


