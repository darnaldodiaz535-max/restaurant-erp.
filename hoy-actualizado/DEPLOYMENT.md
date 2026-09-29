# Publicar el sistema para que otros puedan entrar

La aplicación está preparada para publicarse como un servicio Docker en Render y conectarse a Oracle Autonomous Database. Render sirve la página y el API por HTTPS; Oracle guarda los datos y las fotos. El archivo `render.yaml` define el servicio, pero no contiene las claves de Oracle.

## 1. Crear la base Oracle en la nube

1. Crea una cuenta Oracle Cloud y una Autonomous AI Database Always Free con carga de trabajo Transaction Processing.
2. En la configuración de conexiones, habilita autenticación TLS. Usa el acceso TLS con JDBC Thin; así no hace falta subir un wallet.
3. Conéctate a la base nueva desde SQL Developer como `ADMIN` y crea el usuario de aplicación `RESTAURANTE_APP`. Guarda esa contraseña solo en el gestor de claves de Oracle/Render, no en un archivo del proyecto.
4. Conéctate a la base nueva como `RESTAURANTE_APP`.

## 2. Llevar las tablas y datos locales a Oracle Cloud

Para conservar los registros actuales, exporta desde SQL Developer las tablas `APP_%` de la conexión local `RESTAURANTE_APP`, incluyendo DDL y datos en un archivo SQL. Ejecuta ese archivo en la conexión cloud como `RESTAURANTE_APP`. Antes de importarlo, confirma que la base cloud no tenga ya tablas `APP_`.

Después ejecuta, si no vinieron en esa exportación, `APP_USUARIO.sql` y `APP_ACTIVIDAD.sql`; termina con `APP_PERMISOS_Y_EVIDENCIAS.sql`. Este último crea la tabla de fotos y amplía los roles. Ejecuta cada script una sola vez.

Si prefieres empezar con una base vacía, ejecuta `APP_ESQUEMA.sql` como `RESTAURANTE_APP`, seguido por `APP_USUARIO.sql`, `APP_ACTIVIDAD.sql` y `APP_PERMISOS_Y_EVIDENCIAS.sql`. No ejecutes `APP_ESQUEMA.sql` encima de tablas que ya existan.

## 3. Permitir la conexión de Render a Oracle

Cuando Render cree el servicio, abre su pestaña **Connect → Outbound** y copia los rangos de salida de la región. Añade esos rangos a la lista de acceso de red de la base Oracle. No abras el acceso a todas las direcciones de internet.

En los detalles de la base Oracle, copia la cadena JDBC de TLS. Render necesita el texto con este formato: `jdbc:oracle:thin:@` seguido de la cadena TLS completa que entrega Oracle.

## 4. Publicar el proyecto

1. Crea un repositorio **privado** de GitHub y sube el proyecto. `RESTAURANTE.sql` está excluido del repositorio porque era un archivo local de configuración; no lo vuelvas a subir.
2. En Render, crea un **Blueprint** conectado a ese repositorio y selecciona `render.yaml`.
3. En las variables secretas del servicio, ingresa `ORACLE_JDBC_URL`, `ORACLE_DB_USER` (`RESTAURANTE_APP`) y `ORACLE_DB_PASSWORD` (la contraseña del usuario de aplicación cloud).
4. Espera que el despliegue termine correctamente. Render mostrará el enlace HTTPS del servicio; comparte ese enlace con el equipo.

## Importante sobre el plan gratuito

Render apaga el servicio gratuito después de 15 minutos sin visitas. Cuando alguien vuelva a abrirlo, el primer inicio puede tardar cerca de un minuto. Para que la aplicación esté disponible rápidamente todo el día, habría que cambiar a un servicio siempre activo de pago.
