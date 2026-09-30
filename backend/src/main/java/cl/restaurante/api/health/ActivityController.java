package cl.restaurante.api.health;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class ActivityController {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    public ActivityController(JdbcTemplate jdbc, RoleAccessService access) { this.jdbc = jdbc; this.access = access; }

    @GetMapping("/overview")
    public Overview overview(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        List<Activity> activities = jdbc.query(
                "SELECT USUARIO, ACCION, MODULO, DETALLE, CREADO_EN FROM APP_ACTIVIDAD ORDER BY CREADO_EN DESC FETCH FIRST 8 ROWS ONLY",
                (rs, row) -> {
                    Timestamp created = rs.getTimestamp("CREADO_EN");
                    return new Activity(rs.getString("USUARIO"), rs.getString("ACCION"), rs.getString("MODULO"),
                            rs.getString("DETALLE"), created == null ? "" : created.toLocalDateTime().format(DISPLAY_TIME));
                });
        List<Alert> alerts = new ArrayList<>();
        if (!access.isOwner(user)) return new Overview(activities,alerts);
        jdbc.query("SELECT USUARIO,MODULO,DETALLE FROM APP_ACTIVIDAD WHERE ACCION IN ('ENVIAR_TAREA','COMPLETAR_TAREA') OR (LOWER(MODULO)='incidencias' AND ACCION='CREAR') ORDER BY CREADO_EN DESC FETCH FIRST 8 ROWS ONLY",
            (rs,n)->new Alert(rs.getString(2).equalsIgnoreCase("incidencias")?"Incidencias":"Tareas diarias","🔔",1,rs.getString(1)+": "+rs.getString(3)))
            .forEach(alerts::add);
        addAlert(alerts, "Inventario", "📦", "insumo(s) con stock bajo", "SELECT COUNT(*) FROM APP_INSUMO WHERE STOCK_ACTUAL <= STOCK_MINIMO");
        addAlert(alerts, "Tareas diarias", "✅", "tarea(s) pendiente(s)", "SELECT COUNT(*) FROM APP_TAREA_DIARIA WHERE UPPER(ESTADO) NOT IN ('COMPLETADA','COMPLETADO','FINALIZADA','FINALIZADO')");
        addAlert(alerts, "Control sanitario", "🌡️", "control(es) que requieren revisión", "SELECT COUNT(*) FROM APP_CONTROL_SANITARIO WHERE UPPER(ESTADO) NOT IN ('OK','CUMPLE','COMPLETADO','COMPLETADA','NORMAL')");
        addAlert(alerts, "Incidencias", "⚠️", "incidencia(s) sin resolver", "SELECT COUNT(*) FROM APP_INCIDENCIA WHERE UPPER(ESTADO) NOT IN ('CERRADA','CERRADO','RESUELTA','RESUELTO')");
        addAlert(alerts, "Compras", "🛒", "compra(s) pendiente(s)", "SELECT COUNT(*) FROM APP_COMPRA WHERE UPPER(ESTADO) NOT IN ('RECIBIDA','RECIBIDO','COMPLETADA','COMPLETADO')");
        return new Overview(activities, alerts);
    }
    private void addAlert(List<Alert> alerts, String module, String icon, String description, String sql) {
        long count = jdbc.queryForObject(sql, Long.class);
        if (count > 0) alerts.add(new Alert(module, icon, count, count + " " + description));
    }
    public record Activity(String username, String action, String module, String detail, String createdAt) {}
    public record Alert(String module, String icon, long count, String message) {}
    public record Overview(List<Activity> activities, List<Alert> alerts) {}
}
