package cl.restaurante.api.health;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/modulos")
@CrossOrigin(origins = "*")
public class ModuleController {
    private final JdbcTemplate jdbc;
    private final ActivityService activity;
    private final RoleAccessService access;
    private final AreaService areas;
    private final NotificationService notifications;
    private final InventoryAlertService inventory;
    private static final Map<String, Def> MODULES = definitions();
    public ModuleController(JdbcTemplate jdbc, ActivityService activity, RoleAccessService access, AreaService areas, NotificationService notifications, InventoryAlertService inventory) { this.inventory=inventory; this.notifications=notifications; this.areas=areas; this.jdbc = jdbc; this.activity = activity; this.access = access; }

    @GetMapping("/{module}")
    public List<Row> list(@PathVariable String module, HttpSession session, @RequestParam(required=false) String area) {
        Def d = definition(module);
        access.requireModuleView(session, module);
        StringBuilder sql = new StringBuilder("SELECT T.").append(d.id).append(" AS RECORD_ID");
        for (int i = 0; i < d.fields.size(); i++) {
            Field f = d.fields.get(i);
            sql.append(", ");
            if (f.kind == Kind.EMPLOYEE) sql.append("E.NOMBRE AS C").append(i).append(", T.").append(f.column).append(" AS R").append(i);
            else sql.append("T.").append(f.column).append(" AS C").append(i);
        }
        sql.append(" FROM ").append(d.table).append(" T");
        Field ref = d.fields.stream().filter(f -> f.kind == Kind.EMPLOYEE).findFirst().orElse(null);
        if (ref != null) sql.append(" LEFT JOIN APP_EMPLEADO E ON E.ID_EMPLEADO = T.").append(ref.column);
        RoleAccessService.User user = access.current(session);
        List<Object> args = new ArrayList<>();
        if ("tareas-diarias".equals(module)) {
            String clause = sql.indexOf(" WHERE ") >= 0 ? " AND " : " WHERE ";
            if ("SIN_AREA".equals(area)) {
                access.current(session);
                sql.append(clause).append("T.AREA IS NULL");
            } else {
                String selected=areas.requireView(session, area);
                sql.append(clause).append("T.AREA=?"); args.add(selected);
            }
        }
        sql.append(" ORDER BY T.").append(d.id);
        return jdbc.query(sql.toString(), (rs, n) -> {
            List<String> values = new ArrayList<>();
            List<Long> references = new ArrayList<>();
            for (int i = 0; i < d.fields.size(); i++) {
                Field f = d.fields.get(i);
                values.add(format(rs.getObject("C" + i), f.kind));
                Object id = f.kind == Kind.EMPLOYEE ? rs.getObject("R" + i) : null;
                references.add(id instanceof Number number ? number.longValue() : null);
            }
            return new Row(rs.getLong("RECORD_ID"), values, references);
        }, args.toArray());
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{module}")
    public ResponseEntity<Map<String, String>> create(@PathVariable String module, @RequestBody Request request, HttpSession session) {
        Def d = definition(module); access.requireModuleEdit(session, module, false); Object[] values = values(d, request, session); validateArea(module, values, session);
        String columns = d.fields.stream().map(Field::column).collect(Collectors.joining(", "));
        String marks = d.fields.stream().map(f -> "?").collect(Collectors.joining(", "));
        String insert="INSERT INTO " + d.table + " (" + columns + ") VALUES (" + marks + ")";
        if("inventario".equals(module)) {
            var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{var st=connection.prepareStatement(insert,new String[]{d.id});for(int i=0;i<values.length;i++)st.setObject(i+1,values[i]);return st;},keys);
            inventory.check(keys.getKey().longValue());
        } else jdbc.update(insert,values);
        notifyChange(module,d,values,"Se te asignó un registro");
        activity.record(session, "CREAR", module, "Se guardó un registro");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "guardado"));
    }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{module}/{id}")
    public ResponseEntity<Map<String, String>> update(@PathVariable String module, @PathVariable long id, @RequestBody Request request, HttpSession session) {
        Def d = definition(module); access.requireModuleEdit(session, module, false); Object[] values = values(d, request, session);
        requireRecord(module, d, id, session);
                if ("tareas-diarias".equals(module)) {
            var previousTask = jdbc.query(
                    "SELECT METODO_COMPLETADO, ESTADO " +
                    "FROM APP_TAREA_DIARIA WHERE ID_TAREA=? FOR UPDATE",
                    (rs, row) -> new String[]{
                            rs.getString(1),
                            rs.getString(2)
                    },
                    id
            );

            if (previousTask.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String previousMethod = previousTask.getFirst()[0];
            String previousState = previousTask.getFirst()[1];

            // Conserva el método cuando el cliente envía solo cinco campos.
            if (request.values().size() == 5) {
                values[5] = previousMethod;
            }

            // Comprueba el estado guardado, no el enviado por el cliente.
            if ("EN_REVISION".equalsIgnoreCase(previousState)
                    && !previousMethod.equals(values[5])) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Revisa primero la evidencia pendiente antes de cambiar el método."
                );
            }
        }
        if("inventario".equals(module) && request.values().size()==5) {
            values[5]=jdbc.queryForObject("SELECT STOCK_MAXIMO FROM APP_INSUMO WHERE ID_INSUMO=?",BigDecimal.class,id);
            if(values[5]!=null && ((BigDecimal)values[5]).compareTo((BigDecimal)values[4])<0)
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"El stock máximo debe ser mayor o igual al mínimo.");
        }
        if ("inventario".equals(module)
        && request.values().size() <= 6) {
    values[6] = jdbc.queryForObject(
            "SELECT GRUPO FROM APP_INSUMO WHERE ID_INSUMO=?",
            String.class,
            id
    );
}
        Long previous=recipient(d,id);
        validateArea(module, values, session);
        String sets = d.fields.stream().map(f -> f.column + " = ?").collect(Collectors.joining(", "));
        Object[] params = java.util.Arrays.copyOf(values, values.length + 1); params[values.length] = id;
        int changed = jdbc.update("UPDATE " + d.table + " SET " + sets + " WHERE " + d.id + " = ?", params);
        if (changed == 0) return ResponseEntity.notFound().build();
        if("inventario".equals(module)) inventory.check(id);
        if ("tareas-diarias".equals(module)) {
            RoleAccessService.User user = access.current(session);
            if (access.isManager(user)) jdbc.update("UPDATE APP_TAREA_DIARIA SET ESTADO='PENDIENTE' WHERE ID_TAREA=? AND UPPER(ESTADO)='EN_REVISION'", id);
        }
        notifyChange(module,d,values,"Se actualizó un registro asignado a ti");
        if(previous!=null && Set.of("tareas-diarias","turnos","incidencias").contains(module) && !previous.equals(recipient(d,id))) notifications.employee(previous,label(module),"Se retiró o reasignó un registro que tenías asignado.");
        activity.record(session, "ACTUALIZAR", module, "Se actualizaron los datos");
        return ResponseEntity.ok(Map.of("status", "actualizado"));
    }

    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{module}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String module, @PathVariable long id, HttpSession session) {
        Def d = definition(module); access.requireModuleEdit(session, module, true);
        requireRecord(module, d, id, session);
        Long previous=recipient(d,id);
        if ("tareas-diarias".equals(module)) jdbc.update("DELETE FROM APP_EVIDENCIA_TAREA WHERE ID_TAREA=?", id);
        int changed = jdbc.update("DELETE FROM " + d.table + " WHERE " + d.id + " = ?", id);
        if (changed == 0) return ResponseEntity.notFound().build();
        if(previous!=null && Set.of("tareas-diarias","turnos","incidencias").contains(module)) notifications.employee(previous,label(module),"Se eliminó un registro que tenías asignado.");
        activity.record(session, "ELIMINAR", module, "Se eliminó un registro");
        return ResponseEntity.noContent().build();
    }

    private String label(String module) { return switch(module) { case "turnos" -> "Horarios"; case "tareas-diarias" -> "Tareas diarias"; case "incidencias" -> "Incidencias"; default -> module; }; }
    private Long recipient(Def d,long id) {
        var ref=d.fields.stream().filter(f->f.kind==Kind.EMPLOYEE).findFirst().orElse(null);
        if(ref==null) return null;
        var ids=jdbc.query("SELECT "+ref.column+" FROM "+d.table+" WHERE "+d.id+"=?",(rs,n)->rs.getObject(1)==null?null:rs.getLong(1),id);
        return ids.isEmpty()?null:ids.getFirst();
    }
    private void notifyChange(String module,Def d,Object[] values,String message) {
        if(!Set.of("tareas-diarias","turnos","incidencias").contains(module)) return;
        String detail="tareas-diarias".equals(module)?"Tarea: "+values[0]+". Fecha: "+values[2]+". Área: "+values[4]:"Consulta el módulo para ver los detalles.";
        for(int i=0;i<d.fields.size();i++) if(d.fields.get(i).kind==Kind.EMPLOYEE) notifications.employee(((Number)values[i]).longValue(),label(module),message+" en "+label(module)+". "+detail);
        if("incidencias".equals(module)) notifications.administrators("Incidencias","Se registró o actualizó una incidencia.");
        if("tareas-diarias".equals(module) && "COMPLETADA".equalsIgnoreCase(String.valueOf(values[3]))) notifications.administrators("Tareas diarias","Se completó una tarea.");
    }

    private void validateArea(String module, Object[] values, HttpSession session) {
        if ("tareas-diarias".equals(module)) {
            String area=areas.requireView(session, values[4].toString());
            if (!area.equals(areas.employeeArea(((Number)values[1]).longValue())))
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "El responsable debe pertenecer al área de la tarea.");
            values[4]=area;
        }
        if ("incidencias".equals(module) && access.isWorker(access.current(session))) {
            values[1]=areas.employeeArea(access.current(session).employeeId());
            values[4]="PENDIENTE";
        }
    }

    private void requireRecord(String module, Def d, long id, HttpSession session) {
        var user=access.current(session);
        if (access.isOwner(user) || access.isSalon(user)) return;
        Field ref=d.fields.stream().filter(f -> f.kind==Kind.EMPLOYEE).findFirst().orElse(null);
        if (ref==null) return;
        String sql="SELECT COUNT(*) FROM "+d.table+" T JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=T."+ref.column+" WHERE T."+d.id+"=?";
        Integer found;
        if (access.isWorker(user)) found=jdbc.queryForObject(sql+" AND E.ID_EMPLEADO=?", Integer.class, id, user.employeeId());
        else found=jdbc.queryForObject(sql+" AND "+AreaService.sql("E.AREA")+"='COCINA'", Integer.class,id);
        if (found==null || found==0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar un registro de otro trabajador o área.");
        if ("tareas-diarias".equals(module) && (!access.isOwner(user) && access.isKitchen(user))) {
            Integer same=jdbc.queryForObject("SELECT COUNT(*) FROM APP_TAREA_DIARIA WHERE ID_TAREA=? AND AREA='COCINA'",Integer.class,id);
            if (same==null || same==0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "La tarea no pertenece a Cocina.");
        }
    }

    private Def definition(String module) {
        Def d = MODULES.get(module);
        if (d == null) throw new IllegalArgumentException("Módulo no reconocido.");
        return d;
    }

    private Object[] values(Def d, Request request, HttpSession session) {
        if ("APP_TAREA_DIARIA".equals(d.table)
        && request != null
        && request.values() != null
        && request.values().size() == 5) {
    var legacy = new ArrayList<>(request.values());
    legacy.add("FOTO");
    request = new Request(legacy, request.references());
}
    if ("APP_INSUMO".equals(d.table)
        && request != null
        && request.values() != null
        && (request.values().size() == 5
            || request.values().size() == 6)) {

    var legacy = new ArrayList<>(request.values());

    if (legacy.size() == 5) {
        legacy.add("");
    }

    legacy.add("");
    request = new Request(legacy, request.references());
}
        if (request == null || request.values == null || request.values.size() != d.fields.size())
            throw new IllegalArgumentException("Completa todos los campos.");
        Object[] result = new Object[d.fields.size()];
        for (int i = 0; i < result.length; i++) {
            Field f = d.fields.get(i); String value = request.values.get(i);
            try {
                result[i] = switch (f.kind) {
                    case EMPLOYEE -> {
                        RoleAccessService.User user = access.current(session);
                        if (access.isWorker(user)) yield user.employeeId();
                        Long id = request.references == null || request.references.size() <= i ? null : request.references.get(i);
                        if (id == null) throw new IllegalArgumentException("Selecciona un trabajador o responsable.");
                        if (!access.isOwner(user) && access.isKitchen(user)) access.requireKitchenEmployee(id);
                        yield id;
                    }
                    case NUMBER -> {
                        if("STOCK_MAXIMO".equals(f.column) && (value==null || value.isBlank())) yield null;
                        if (value == null || value.isBlank()) throw new IllegalArgumentException("Completa " + f.label + ".");
                        yield new BigDecimal(value);
                    }
                    case DATE -> {
                        if (value == null || value.isBlank()) throw new IllegalArgumentException("Completa " + f.label + ".");
                        yield java.sql.Date.valueOf(LocalDate.parse(value));
                    }
                    case TIMESTAMP -> {
                        if (value == null || value.isBlank()) throw new IllegalArgumentException("Completa " + f.label + ".");
                        yield Timestamp.valueOf(LocalDateTime.parse(value));
                    }
                case TEXT -> {
    if ("APP_INSUMO".equals(d.table)
            && "GRUPO".equals(f.column)
            && (value == null || value.isBlank())) {
        yield null;
    }

    if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(
                "Completa " + f.label + ".");
    }

    yield value.trim();
}
                };
            } catch (NumberFormatException | java.time.DateTimeException ex) {
                throw new IllegalArgumentException("Revisa el valor de " + f.label + ".");
            }
        }
        if("APP_INSUMO".equals(d.table)) {
            BigDecimal current=(BigDecimal)result[2],min=(BigDecimal)result[4],max=(BigDecimal)result[5];
            if(current.signum()<0 || min.signum()<0 || (max!=null && (max.signum()<0 || max.compareTo(min)<0)))
                throw new IllegalArgumentException("El stock no puede ser negativo y el máximo debe ser mayor o igual al mínimo.");
        }
        if ("APP_TAREA_DIARIA".equals(d.table)) {
    String method = String.valueOf(result[5])
            .trim()
            .toUpperCase(java.util.Locale.ROOT);

    if (!Set.of("SIMPLE", "FOTO").contains(method)) {
        throw new IllegalArgumentException(
                "Selecciona Marcar como hecha o Enviar foto."
        );
    }

    result[5] = method;
}
if ("APP_INSUMO".equals(d.table) && result[6] != null) {
    String group = result[6].toString()
            .trim()
            .toUpperCase(java.util.Locale.ROOT);

    if (!Set.of("COCINA", "BARRA").contains(group)) {
        throw new IllegalArgumentException(
                "Selecciona Cocina o Barra.");
    }

    result[6] = group;
}
        return result;
    }

    private String format(Object value, Kind kind) {
        if (value == null) return "";
        if (kind == Kind.DATE && value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (kind == Kind.DATE && value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate().toString();
        if (kind == Kind.TIMESTAMP && value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toString().substring(0, 16);
        return value.toString();
    }
    private static Field t(String label, String col) { return new Field(label, col, Kind.TEXT); }
    private static Field n(String label, String col) { return new Field(label, col, Kind.NUMBER); }
    private static Field d(String label, String col) { return new Field(label, col, Kind.DATE); }
    private static Field ts(String label, String col) { return new Field(label, col, Kind.TIMESTAMP); }
    private static Field e(String label, String col) { return new Field(label, col, Kind.EMPLOYEE); }
    private static Def def(String table, String id, Field... fields) { return new Def(table, id, List.of(fields)); }

    private static Map<String, Def> definitions() {
        Map<String, Def> m = new LinkedHashMap<>();
        m.put("asistencia", def("APP_ASISTENCIA","ID_ASISTENCIA",e("Trabajador","EMPLEADO_ID"),d("Fecha","FECHA"),t("Hora de entrada","HORA_ENTRADA"),t("Hora de salida","HORA_SALIDA"),t("Estado","ESTADO")));
        m.put("cocina", def("APP_COCINA","ID_COCINA",t("Preparación","PREPARACION"),e("Responsable","RESPONSABLE_ID"),t("Hora","HORA"),t("Estado","ESTADO")));
        m.put("compras", def("APP_COMPRA","ID_COMPRA",t("Producto","PRODUCTO"),n("Cantidad","CANTIDAD"),t("Proveedor","PROVEEDOR"),d("Fecha","FECHA"),t("Estado","ESTADO")));
        m.put("configuracion", def("APP_CONFIGURACION","ID_CONFIGURACION",t("Opción","OPCION"),t("Valor","VALOR"),t("Descripción","DESCRIPCION")));
        m.put("control-sanitario", def("APP_CONTROL_SANITARIO","ID_CONTROL",t("Punto de control","PUNTO_CONTROL"),n("Temperatura (°C)","TEMPERATURA_C"),ts("Fecha y hora","FECHA_HORA"),e("Responsable","RESPONSABLE_ID"),t("Estado","ESTADO")));
        m.put("incidencias", def("APP_INCIDENCIA","ID_INCIDENCIA",t("Tipo","TIPO"),t("Área","AREA"),t("Descripción","DESCRIPCION"),e("Responsable","RESPONSABLE_ID"),t("Estado","ESTADO")));
        m.put("inventario", def(
        "APP_INSUMO", "ID_INSUMO",
        t("Insumo", "NOMBRE"),
        t("Categoría", "CATEGORIA"),
        n("Stock actual", "STOCK_ACTUAL"),
        t("Unidad", "UNIDAD"),
        n("Stock mínimo", "STOCK_MINIMO"),
        n("Stock máximo", "STOCK_MAXIMO"),
        t("Grupo", "GRUPO")
));
        m.put("limpieza", def("APP_LIMPIEZA","ID_LIMPIEZA",t("Área","AREA"),t("Tarea","TAREA"),e("Responsable","RESPONSABLE_ID"),t("Frecuencia","FRECUENCIA"),t("Estado","ESTADO")));
        m.put("mermas", def("APP_MERMA","ID_MERMA",t("Producto","PRODUCTO"),n("Cantidad","CANTIDAD"),t("Unidad","UNIDAD"),t("Motivo","MOTIVO"),d("Fecha","FECHA")));
        m.put("mise-en-place", def("APP_MISE_EN_PLACE","ID_MISE",t("Preparación","PREPARACION"),n("Cantidad","CANTIDAD"),e("Responsable","RESPONSABLE_ID"),t("Hora límite","HORA_LIMITE"),t("Estado","ESTADO")));
        m.put("produccion", def("APP_PRODUCCION","ID_PRODUCCION",t("Preparación","PREPARACION"),n("Cantidad","CANTIDAD"),t("Unidad","UNIDAD"),e("Responsable","RESPONSABLE_ID"),d("Fecha","FECHA")));
        m.put("reportes", def("APP_REPORTE","ID_REPORTE",t("Nombre del reporte","NOMBRE"),t("Período","PERIODO"),d("Fecha","FECHA"),t("Estado","ESTADO")));
        m.put("reservas", def("APP_RESERVA","ID_RESERVA",t("Cliente","CLIENTE"),d("Fecha","FECHA"),t("Hora","HORA"),n("Personas","PERSONAS"),t("Contacto","CONTACTO"),t("Estado","ESTADO")));
       m.put("tareas-diarias", def(
        "APP_TAREA_DIARIA", "ID_TAREA",
        t("Tarea", "TAREA"),
        e("Responsable", "RESPONSABLE_ID"),
        d("Fecha", "FECHA"),
        t("Estado", "ESTADO"),
        t("Área", "AREA"),
        t("Método", "METODO_COMPLETADO")
));
        m.put("turnos", def("APP_TURNO","ID_TURNO",e("Trabajador","EMPLEADO_ID"),d("Fecha","FECHA"),t("Hora de inicio","HORA_INICIO"),t("Hora de término","HORA_TERMINO"),t("Área","AREA")));
        return Map.copyOf(m);
    }
    private enum Kind { TEXT, NUMBER, DATE, TIMESTAMP, EMPLOYEE }
    private record Field(String label, String column, Kind kind) {}
    private record Def(String table, String id, List<Field> fields) {}
    public record Request(List<String> values, List<Long> references) {}
    public record Row(long id, List<String> values, List<Long> references) {}
}



