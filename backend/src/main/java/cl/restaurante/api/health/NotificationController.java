package cl.restaurante.api.health;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificationController {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    public NotificationController(JdbcTemplate jdbc,RoleAccessService access) { this.jdbc=jdbc;this.access=access; }
@GetMapping
public Page list(
        HttpSession session,
        @RequestParam(defaultValue = "0") long before) {
    long user = access.current(session).id();

    var rows = jdbc.query(
            """
            SELECT ID_NOTIFICACION, MODULO, MENSAJE, DETALLE, LEIDA, CREADO_EN
            FROM APP_NOTIFICACION
            WHERE ID_USUARIO = ?
              AND VISIBLE = 'S'
              AND (? = 0 OR ID_NOTIFICACION < ?)
            ORDER BY ID_NOTIFICACION DESC
            FETCH FIRST 51 ROWS ONLY
            """,
            (rs, rowNum) -> {
                String detail = NotificationText.readClob(rs, "DETALLE");
                String message = detail == null
                        ? rs.getString("MENSAJE")
                        : detail;

                return new Notice(
                        rs.getLong("ID_NOTIFICACION"),
                        rs.getString("MODULO"),
                        message,
                        "S".equals(rs.getString("LEIDA")),
                        rs.getTimestamp("CREADO_EN").toString());
            },
            user,
            before,
            before);

    long unread = jdbc.queryForObject(
            "SELECT COUNT(*) FROM APP_NOTIFICACION "
                    + "WHERE ID_USUARIO=? AND VISIBLE='S' AND LEIDA='N'",
            Long.class,
            user);

    return new Page(unread, rows.stream().limit(50).toList(), rows.size() > 50);
}
    @PostMapping("/{id}/leida")
    public Map<String,String> read(@PathVariable long id,HttpSession session) {
        if(jdbc.update("UPDATE APP_NOTIFICACION SET LEIDA='S' WHERE ID_NOTIFICACION=? AND ID_USUARIO=?",id,access.current(session).id())==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notificación no encontrada.");
        return Map.of("status","leída");
    }
    @PostMapping("/leidas")
    public Map<String,String> readAll(HttpSession session) {
        jdbc.update("UPDATE APP_NOTIFICACION SET LEIDA='S' WHERE ID_USUARIO=? AND LEIDA='N'",access.current(session).id());
        return Map.of("status","leídas");
    }
        @DeleteMapping("/{id}")
    public Map<String, String> hide(
            @PathVariable long id,
            HttpSession session) {
        long userId = access.current(session).id();

        int changed = jdbc.update(
                "UPDATE APP_NOTIFICACION "
                        + "SET VISIBLE='N' "
                        + "WHERE ID_NOTIFICACION=? "
                        + "AND ID_USUARIO=? "
                        + "AND VISIBLE='S'",
                id,
                userId);

        if (changed == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Notificación no encontrada.");
        }

        return Map.of("status", "eliminada");
    }

    @DeleteMapping
    public Map<String, Object> hideAll(HttpSession session) {
        long userId = access.current(session).id();

        int changed = jdbc.update(
                "UPDATE APP_NOTIFICACION "
                        + "SET VISIBLE='N' "
                        + "WHERE ID_USUARIO=? "
                        + "AND VISIBLE='S'",
                userId);

        return Map.of(
                "status", "eliminadas",
                "count", changed);
    }
    public record Notice(long id,String module,String message,boolean read,String createdAt) {}
    public record Page(long unread,List<Notice> items,boolean hasMore) {}
}
