-- Ejecutar manualmente como RESTAPI_HOY2609 en Oracle Database Actions (F5).
-- Añade los roles de jefe de barra y copería sin alterar cuentas ni datos.
DECLARE
    n NUMBER;
BEGIN
    IF USER <> 'RESTAPI_HOY2609' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Conéctate como RESTAPI_HOY2609.');
    END IF;

    SELECT COUNT(*) INTO n FROM user_constraints
      WHERE table_name='APP_USUARIO' AND constraint_name='CK_APP_USUARIO_ROL_V3';
    IF n=0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE APP_USUARIO ADD CONSTRAINT CK_APP_USUARIO_ROL_V3
          CHECK (ROL IN ('ADMIN','EMPRESA','JEFE_SALON','JEFE_COCINA','JEFE_BARRA','JEFE_COPERIA','JEFE_LOCAL','TRABAJADOR','EMPLEADO')) ENABLE VALIDATE]';
    END IF;

    FOR c IN (SELECT constraint_name FROM user_constraints
              WHERE table_name='APP_USUARIO' AND constraint_type='C'
                AND constraint_name IN ('CK_APP_USUARIO_ROL_V2','CK_APP_USUARIO_ROL')) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE APP_USUARIO DROP CONSTRAINT ' || DBMS_ASSERT.SIMPLE_SQL_NAME(c.constraint_name);
    END LOOP;
END;
/
SELECT constraint_name, status, validated FROM user_constraints
WHERE table_name='APP_USUARIO' AND constraint_name='CK_APP_USUARIO_ROL_V3';
