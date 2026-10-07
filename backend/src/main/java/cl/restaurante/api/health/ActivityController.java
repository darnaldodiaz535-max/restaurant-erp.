package cl.restaurante.api.health;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dashboard")
public class ActivityController {
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Set<String> COUNT_ALERT_KEYS = Set.of(
            "INVENTARIO_BAJO",
            "TAREAS_PENDIENTES",
            "CONTROL_SANITARIO_PENDIENTE",
            "INCIDENCIAS_PENDIENTES",
            "COMPRAS_PENDIENTES");

    private final JdbcTemplate jdbc;
    private final RoleAccessService access;

    public ActivityController(JdbcTemplate jdbc, RoleAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping("/overview")
    public Overview overview(HttpSession session) {
        RoleAccessService.User user = access.current(session);

        List<Activity> activities = jdbc.query(
                """
                SELECT ID_ACTIVIDAD, USUARIO, ACCION, MODULO, DETALLE, CREADO_EN
                FROM APP_ACTIVIDAD
                WHERE OCULTA = 'N'
                ORDER BY CREADO_EN DESC, ID_ACTIVIDAD DESC
                FETCH FIRST 8 ROWS ONLY
                """,
                (rs, row) -> {
                    Timestamp created = rs.getTimestamp("CREADO_EN");
                    return new Activity(
                            rs.getLong("ID_ACTIVIDAD"),
                            rs.getString("USUARIO"),
                            rs.getString("ACCION"),
                            rs.getString("MODULO"),
                            rs.getString("DETALLE"),
                            created == null
                                    ? ""
                                    : created.toLocalDateTime().format(DISPLAY_TIME));
                });

        List<Alert> alerts = new ArrayList<>();
        if (!access.isOwner(user)) {
            return new Overview(activities, alerts);
        }

        List<EventAlert> eventAlerts = jdbc.query(
                """
                SELECT A.ID_ACTIVIDAD, A.USUARIO, A.MODULO, A.DETALLE
                FROM APP_ACTIVIDAD A
                WHERE A.OCULTA = 'N'
                  AND (
                        A.ACCION IN ('ENVIAR_TAREA', 'COMPLETAR_TAREA')
                     OR (LOWER(A.MODULO) = 'incidencias' AND A.ACCION = 'CREAR')
                  )
                  AND NOT EXISTS (
                        SELECT 1
                        FROM APP_ALERTA_DESCARTADA D
                        WHERE D.CLAVE_ALERTA =
                              'ACTIVIDAD_EVENTO_' || TO_CHAR(A.ID_ACTIVIDAD)
                  )
                ORDER BY A.CREADO_EN DESC, A.ID_ACTIVIDAD DESC
                FETCH FIRST 8 ROWS ONLY
                """,
                (rs, row) -> new EventAlert(
                        rs.getLong("ID_ACTIVIDAD"),
                        rs.getString("USUARIO"),
                        rs.getString("MODULO"),
                        rs.getString("DETALLE")));

        for (EventAlert event : eventAlerts) {
            boolean incident = "incidencias".equalsIgnoreCase(event.module());
            String key = "ACTIVIDAD_EVENTO_" + event.id();
            alerts.add(new Alert(
                    key,
                    incident ? "Incidencias" : "Tareas diarias",
                    "🔔",
                    1,
                    event.username() + ": " + event.detail()));
        }

        addCountAlert(
                alerts,
                "INVENTARIO_BAJO",
                "Inventario",
                "📦",
                "insumo(s) con stock bajo",
                "SELECT COUNT(*) FROM APP_INSUMO "
                        + "WHERE STOCK_ACTUAL < STOCK_MINIMO");

        addCountAlert(
                alerts,
                "TAREAS_PENDIENTES",
                "Tareas diarias",
                "✅",
                "tarea(s) pendiente(s)",
                "SELECT COUNT(*) FROM APP_TAREA_DIARIA "
                        + "WHERE UPPER(ESTADO) NOT IN "
                        + "('COMPLETADA','COMPLETADO','FINALIZADA','FINALIZADO')");

        addCountAlert(
                alerts,
                "CONTROL_SANITARIO_PENDIENTE",
                "Control sanitario",
                "🌡️",
                "control(es) que requieren revisión",
                "SELECT COUNT(*) FROM APP_CONTROL_SANITARIO "
                        + "WHERE UPPER(ESTADO) NOT IN "
                        + "('OK','CUMPLE','COMPLETADO','COMPLETADA','NORMAL')");

        addCountAlert(
                alerts,
                "INCIDENCIAS_PENDIENTES",
                "Incidencias",
                "⚠️",
                "incidencia(s) sin resolver",
                "SELECT COUNT(*) FROM APP_INCIDENCIA "
                        + "WHERE UPPER(ESTADO) NOT IN "
                        + "('CERRADA','CERRADO','RESUELTA','RESUELTO')");

        addCountAlert(
                alerts,
                "COMPRAS_PENDIENTES",
                "Compras",
                "🛒",
                "compra(s) pendiente(s)",
                "SELECT COUNT(*) FROM APP_COMPRA "
                        + "WHERE UPPER(ESTADO) NOT IN "
                        + "('RECIBIDA','RECIBIDO','COMPLETADA','COMPLETADO')");

        return new Overview(activities, alerts);
    }

    @DeleteMapping("/activity/{id}")
    @Transactional
    public ResponseEntity<Void> hideActivity(
            @PathVariable long id,
            HttpSession session) {
        access.requireOwner(session);

        int changed = jdbc.update(
                "UPDATE APP_ACTIVIDAD SET OCULTA='S' "
                        + "WHERE ID_ACTIVIDAD=? AND OCULTA='N'",
                id);

        if (changed == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/alerts/{key}")
    @Transactional
    public ResponseEntity<Void> dismissAlert(
            @PathVariable String key,
            HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isOwner(user)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes permiso para descartar alertas.");
        }

        if (!isValidAlertKey(key)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La alerta seleccionada no es válida.");
        }

        try {
            jdbc.update(
                    "INSERT INTO APP_ALERTA_DESCARTADA "
                            + "(CLAVE_ALERTA, DESCARTADA_POR) VALUES (?, ?)",
                    key,
                    user.id());
        } catch (DuplicateKeyException ignored) {
            // Descartar otra vez la misma alerta es idempotente.
        }

        return ResponseEntity.noContent().build();
    }

    private void addCountAlert(
            List<Alert> alerts,
            String key,
            String module,
            String icon,
            String description,
            String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        long value = count == null ? 0 : count;

        if (value == 0) {
            jdbc.update(
                    "DELETE FROM APP_ALERTA_DESCARTADA WHERE CLAVE_ALERTA=?",
                    key);
            return;
        }

        if (!isDismissed(key)) {
            alerts.add(new Alert(
                    key,
                    module,
                    icon,
                    value,
                    value + " " + description));
        }
    }

    private boolean isDismissed(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM APP_ALERTA_DESCARTADA "
                        + "WHERE CLAVE_ALERTA=?",
                Integer.class,
                key);
        return count != null && count > 0;
    }

    private boolean isValidAlertKey(String key) {
        return COUNT_ALERT_KEYS.contains(key)
                || (key != null
                && key.matches("ACTIVIDAD_EVENTO_[1-9][0-9]*"));
    }

    public record Activity(
            long id,
            String username,
            String action,
            String module,
            String detail,
            String createdAt) {}

    public record Alert(
            String id,
            String module,
            String icon,
            long count,
            String message) {}

    public record Overview(List<Activity> activities, List<Alert> alerts) {}

    private record EventAlert(
            long id,
            String username,
            String module,
            String detail) {}
}