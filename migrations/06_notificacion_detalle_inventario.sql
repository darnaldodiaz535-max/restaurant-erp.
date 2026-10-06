-- Ejecutar manualmente en Oracle como el dueño del esquema MariGex.
-- Es aditiva e idempotente. No elimina ni modifica avisos existentes.
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
    n NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO n
      FROM USER_TABLES
     WHERE TABLE_NAME = 'APP_NOTIFICACION';

    IF n = 0 THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'No existe APP_NOTIFICACION en el esquema actual.');
    END IF;

    SELECT COUNT(*)
      INTO n
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'APP_NOTIFICACION'
       AND COLUMN_NAME = 'DETALLE';

    IF n = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE APP_NOTIFICACION ADD (DETALLE CLOB)';
    END IF;
END;
/

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
  FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'APP_NOTIFICACION'
   AND COLUMN_NAME IN ('MENSAJE', 'DETALLE')
 ORDER BY COLUMN_ID;