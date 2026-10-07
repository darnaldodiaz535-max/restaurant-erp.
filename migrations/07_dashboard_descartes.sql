-- Agrega ocultamiento lógico del historial y persistencia para descartar alertas.
-- No borra ni modifica actividades, productos, tareas ni otros registros existentes.
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
    n NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO n
      FROM USER_TABLES
     WHERE TABLE_NAME = 'APP_ACTIVIDAD';

    IF n = 0 THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'No existe APP_ACTIVIDAD en el esquema actual.');
    END IF;

    SELECT COUNT(*)
      INTO n
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'APP_ACTIVIDAD'
       AND COLUMN_NAME = 'OCULTA';

    IF n = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE APP_ACTIVIDAD '
            || 'ADD (OCULTA CHAR(1) DEFAULT ''N'' NOT NULL)';
    END IF;

    SELECT COUNT(*)
      INTO n
      FROM USER_TABLES
     WHERE TABLE_NAME = 'APP_ALERTA_DESCARTADA';

    IF n = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE APP_ALERTA_DESCARTADA (
                CLAVE_ALERTA VARCHAR2(160 CHAR) NOT NULL,
                DESCARTADA_POR NUMBER NOT NULL,
                DESCARTADA_EN TIMESTAMP(6)
                    DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT PK_APP_ALERTA_DESCARTADA
                    PRIMARY KEY (CLAVE_ALERTA),
                CONSTRAINT FK_ALERTA_DESC_USUARIO
                    FOREIGN KEY (DESCARTADA_POR)
                    REFERENCES APP_USUARIO (ID_USUARIO)
                    ON DELETE CASCADE
            )';
    END IF;
END;
/

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
  FROM USER_TAB_COLUMNS
 WHERE (TABLE_NAME = 'APP_ACTIVIDAD' AND COLUMN_NAME = 'OCULTA')
    OR TABLE_NAME = 'APP_ALERTA_DESCARTADA'
 ORDER BY TABLE_NAME, COLUMN_ID;