-- Ejecutar manualmente como RESTAPI_HOY2609 en Oracle Cloud (F5).
-- No crea tablas/columnas ni cambia datos, usuarios, hashes o contraseñas.
-- Agrega JEFE_LOCAL a los roles admitidos. DDL confirma automáticamente.
DECLARE
    n NUMBER;
BEGIN
    IF USER <> 'RESTAPI_HOY2609' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Conéctate como RESTAPI_HOY2609.');
    END IF;
    SELECT COUNT(*) INTO n FROM user_constraints
      WHERE table_name='APP_USUARIO' AND constraint_name='CK_APP_USUARIO_ROL_V2';
    IF n=0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE APP_USUARIO ADD CONSTRAINT CK_APP_USUARIO_ROL_V2
          CHECK (ROL IN ('ADMIN','EMPRESA','JEFE_SALON','JEFE_COCINA','JEFE_LOCAL','TRABAJADOR','EMPLEADO')) ENABLE VALIDATE]';
    END IF;
    -- La nueva validación se instala primero para no dejar la tabla sin control.
    SELECT COUNT(*) INTO n FROM user_constraints
      WHERE table_name='APP_USUARIO' AND constraint_name='CK_APP_USUARIO_ROL';
    IF n>0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE APP_USUARIO DROP CONSTRAINT CK_APP_USUARIO_ROL';
    END IF;
END;
/
SELECT constraint_name, status, validated FROM user_constraints
WHERE table_name='APP_USUARIO' AND constraint_name='CK_APP_USUARIO_ROL_V2';
