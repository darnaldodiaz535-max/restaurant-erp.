-- Permite guardar horarios de personal que no tiene RUT (plantillas globales).
-- Ejecutar una vez en Oracle; se puede volver a ejecutar.
WHENEVER SQLERROR EXIT SQL.SQLCODE
DECLARE
    n NUMBER;
BEGIN
    SELECT COUNT(*) INTO n
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME='APP_ASISTENCIA_HORARIO'
       AND COLUMN_NAME='RUT_SNAPSHOT'
       AND NULLABLE='N';
    IF n>0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE APP_ASISTENCIA_HORARIO MODIFY (RUT_SNAPSHOT NULL)';
    END IF;
END;
/
