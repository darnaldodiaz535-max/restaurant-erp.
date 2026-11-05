package cl.restaurante.api.health;

import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Explicit ownership rules: remove personal data without cascading into other employees. */
@Service
public class EmployeeDeletionService {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;
    public EmployeeDeletionService(JdbcTemplate jdbc, RoleAccessService access, ActivityService activity) {
        this.jdbc=jdbc; this.access=access; this.activity=activity;
    }

    @Transactional
    public boolean delete(long employee, HttpSession session) {
        var actor=access.current(session);
        if (!access.isOwner(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo el jefe de empresa puede eliminar personal.");
        if (actor.employeeId()==employee) throw new ResponseStatusException(HttpStatus.CONFLICT,"No puedes eliminar el trabajador asociado a la cuenta que está en uso.");
        Schema schema=schema();
        if (!schema.has("APP_EMPLEADO","ID_EMPLEADO") || !schema.has("APP_USUARIO","ID_USUARIO","ID_EMPLEADO"))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"No se pudo revisar la estructura de Personal. No se eliminó ningún registro.");
        if (schema.oracle()) jdbc.execute("ALTER SESSION DISABLE PARALLEL DML");
        // Use the same lock order as Check-in generation/confirmation.
        if (schema.has("APP_CHECKIN_CONFIG","INICIO"))
            jdbc.query("SELECT INICIO FROM APP_CHECKIN_CONFIG WHERE ID=1 FOR UPDATE", (rs,n)->rs.getDate(1));
        if (jdbc.query("SELECT ID_EMPLEADO FROM APP_EMPLEADO WHERE ID_EMPLEADO=? FOR UPDATE",(rs,n)->rs.getLong(1),employee).isEmpty()) return false;
        var users=jdbc.query("SELECT ID_USUARIO,USUARIO FROM APP_USUARIO WHERE ID_EMPLEADO=? FOR UPDATE",
                (rs,n)->new Account(rs.getLong(1),rs.getString(2)),employee);
        for (Account user:users) {
            requireSharedAuthorNullable(schema,"APP_ASISTENCIA_PLANILLA","CREADO_POR",user.id());
            requireSharedAuthorNullable(schema,"APP_FERIADO_USO","REGISTRADO_POR",user.id());
            requireSharedAuthorNullable(schema,"APP_HORARIO_IMAGEN","CREADO_POR",user.id());
        }

        // Photos of assigned personal tasks, then the tasks themselves.
        if (schema.has("APP_EVIDENCIA_TAREA","ID_TAREA") && schema.has("APP_TAREA_DIARIA","ID_TAREA","RESPONSABLE_ID"))
            jdbc.update("DELETE FROM APP_EVIDENCIA_TAREA WHERE ID_TAREA IN (SELECT ID_TAREA FROM APP_TAREA_DIARIA WHERE RESPONSABLE_ID=?)",employee);
        remove(schema,"APP_EVIDENCIA_TAREA","ID_EMPLEADO",employee);
        for (String table:List.of("APP_TAREA_DIARIA","APP_COCINA","APP_PRODUCCION","APP_MISE_EN_PLACE","APP_LIMPIEZA","APP_INCIDENCIA","APP_CONTROL_SANITARIO"))
            remove(schema,table,"RESPONSABLE_ID",employee);
        for (String table:List.of("APP_ASISTENCIA","APP_TURNO","APP_ASISTENCIA_HORARIO")) remove(schema,table,"EMPLEADO_ID",employee);
        remove(schema,"APP_HORARIO_DESTINATARIO","ID_EMPLEADO",employee);
        remove(schema,"APP_FERIADO_USO","ID_EMPLEADO",employee);
        deletePersonalCheckin(schema,employee);

        Set<AreaWeek> affected=new HashSet<>();
        for (Account user:users) {
            // Area-wide tasks belong to the area, not to the departing chief. Remove
            // their evidence/confirmation and leave the shared task pending.
            if (schema.has("APP_CHECKIN_REGISTRO","ID_RESPONSABLE","AREA","FECHA")) {
                affected.addAll(jdbc.query("SELECT DISTINCT AREA,FECHA FROM APP_CHECKIN_REGISTRO WHERE ID_RESPONSABLE=?",
                        (rs,n)->new AreaWeek(rs.getString(1),rs.getDate(2).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),user.id()));
                if (schema.has("APP_CHECKIN_EVIDENCIA","ID_REGISTRO"))
                    jdbc.update("DELETE FROM APP_CHECKIN_EVIDENCIA WHERE ID_REGISTRO IN (SELECT ID_REGISTRO FROM APP_CHECKIN_REGISTRO WHERE ID_RESPONSABLE=?)",user.id());
                jdbc.update("UPDATE APP_CHECKIN_REGISTRO SET ESTADO='PENDIENTE',ID_RESPONSABLE=NULL,RESPONSABLE=NULL,REALIZADA_EN=NULL WHERE ID_RESPONSABLE=?",user.id());
            }
            for (String column:List.of("ID_REMITENTE","ID_DESTINATARIO")) remove(schema,"APP_CHECKIN_OBSERVACION",column,user.id());
            remove(schema,"APP_CHECKIN_ENVIO_SEMANAL","ID_USUARIO",user.id());
            for (String column:List.of("AUTOR_USUARIO","DESTINATARIO_USUARIO")) remove(schema,"APP_COMUNICACION",column,user.id());
            for (String column:List.of("EVALUADOR_USUARIO","EVALUADO_USUARIO")) remove(schema,"APP_EVAL_JEFATURA",column,user.id());
            remove(schema,"APP_REPORTE_DIARIO","AUTOR_USUARIO",user.id());
            if (schema.has("APP_NOTIF_ENVIO","ID_NOTIFICACION") && schema.has("APP_NOTIFICACION","ID_USUARIO"))
                jdbc.update("DELETE FROM APP_NOTIF_ENVIO WHERE ID_NOTIFICACION IN (SELECT ID_NOTIFICACION FROM APP_NOTIFICACION WHERE ID_USUARIO=?)",user.id());
            for (String table:List.of("APP_NOTIFICACION","APP_NOTIF_PREFERENCIA","APP_NOTIF_MODULO_PREF","APP_PUSH_SUSCRIPCION","APP_PROPUESTA_HORARIO"))
                remove(schema,table,"ID_USUARIO",user.id());
            remove(schema,"APP_ALERTA_DESCARTADA","DESCARTADA_POR",user.id());
            clear(schema,"APP_ASISTENCIA_PLANILLA","CREADO_POR",user.id());
            clear(schema,"APP_HORARIO_IMAGEN","CREADO_POR",user.id());
            clear(schema,"APP_FERIADO_USO","REGISTRADO_POR",user.id());
            remove(schema,"APP_ACTIVIDAD","USUARIO",user.username());
        }
        for (AreaWeek week:affected) refreshSummary(schema,week);
        jdbc.update("DELETE FROM APP_USUARIO WHERE ID_EMPLEADO=?",employee);
        jdbc.update("DELETE FROM APP_EMPLEADO WHERE ID_EMPLEADO=?",employee);
        activity.record(session,"ELIMINAR","Personal","Se eliminó definitivamente el trabajador #"+employee+" y sus registros personales vinculados.");
        return true;
    }

    private void deletePersonalCheckin(Schema schema,long employee) {
        if (schema.has("APP_CHECKIN_DIA","ID_CHECKIN_DIA","ID_EMPLEADO_CLAVE")) {
            if (schema.has("APP_CHECKIN_TAREA","ID_CHECKIN_DIA","ID_CHECKIN_TAREA")) {
                if (schema.has("APP_CHECKIN_FOTO","ID_CHECKIN_TAREA"))
                    jdbc.update("DELETE FROM APP_CHECKIN_FOTO WHERE ID_CHECKIN_TAREA IN (SELECT T.ID_CHECKIN_TAREA FROM APP_CHECKIN_TAREA T JOIN APP_CHECKIN_DIA D ON D.ID_CHECKIN_DIA=T.ID_CHECKIN_DIA WHERE D.ID_EMPLEADO_CLAVE=?)",employee);
                jdbc.update("DELETE FROM APP_CHECKIN_TAREA WHERE ID_CHECKIN_DIA IN (SELECT ID_CHECKIN_DIA FROM APP_CHECKIN_DIA WHERE ID_EMPLEADO_CLAVE=?)",employee);
            }
            remove(schema,"APP_CHECKIN_DIA","ID_EMPLEADO_CLAVE",employee);
        }
        remove(schema,"APP_CHECKIN_AVISO_DIA","ID_EMPLEADO_CLAVE",employee);
    }

    private void refreshSummary(Schema schema,AreaWeek week) {
        if (!schema.has("APP_CHECKIN_RESUMEN","TOTAL","COMPLETADAS","PENDIENTES","SEMANA","AREA")) return;
        var counts=jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(CASE WHEN ESTADO='REALIZADA' THEN 1 ELSE 0 END),0) FROM APP_CHECKIN_REGISTRO WHERE AREA=? AND FECHA>=? AND FECHA<?",
                (rs,n)->new int[]{rs.getInt(1),rs.getInt(2)},week.area(),Date.valueOf(week.start()),Date.valueOf(week.start().plusWeeks(1)));
        jdbc.update("UPDATE APP_CHECKIN_RESUMEN SET TOTAL=?,COMPLETADAS=?,PENDIENTES=? WHERE AREA=? AND SEMANA=?",
                counts[0],counts[1],counts[0]-counts[1],week.area(),Date.valueOf(week.start()));
    }

    private void requireSharedAuthorNullable(Schema schema,String table,String column,long user) {
        if (schema.has(table,column) && !schema.columns().get(table).get(column)
                && jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Integer.class,user)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Antes de eliminar este trabajador aplica migrations/24_eliminacion_personal.sql para conservar los registros compartidos del resto del equipo.");
    }
    private void remove(Schema schema,String table,String column,Object value) {
        if (schema.has(table,column)) jdbc.update("DELETE FROM "+table+" WHERE "+column+"=?",value);
    }
    private void clear(Schema schema,String table,String column,long value) {
        if (schema.has(table,column)) jdbc.update("UPDATE "+table+" SET "+column+"=NULL WHERE "+column+"=?",value);
    }
    private Schema schema() {
        return jdbc.execute((ConnectionCallback<Schema>) connection -> {
            Map<String,Map<String,Boolean>> columns=new HashMap<>();
            var metadata=connection.getMetaData();
            try (var rs=metadata.getColumns(null,connection.getSchema(),"APP_%",null)) {
                while (rs.next()) columns.computeIfAbsent(rs.getString("TABLE_NAME"),ignored->new HashMap<>())
                        .put(rs.getString("COLUMN_NAME"),rs.getInt("NULLABLE")!=DatabaseMetaData.columnNoNulls);
            }
            return new Schema(columns,metadata.getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("oracle"));
        });
    }
    private record Account(long id,String username) {}
    private record AreaWeek(String area,java.time.LocalDate start) {}
    private record Schema(Map<String,Map<String,Boolean>> columns,boolean oracle) {
        boolean has(String table,String... names) {
            return columns.containsKey(table) && java.util.Arrays.stream(names).allMatch(columns.get(table)::containsKey);
        }
    }
}
