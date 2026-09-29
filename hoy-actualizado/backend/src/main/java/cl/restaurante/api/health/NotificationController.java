package cl.restaurante.api.health;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificationController {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    public NotificationController(JdbcTemplate jdbc,RoleAccessService access) { this.jdbc=jdbc;this.access=access; }
    @GetMapping
    public Page list(HttpSession session,@RequestParam(defaultValue="0") long before) {
        long user=access.current(session).id();
        var rows=jdbc.query("SELECT ID_NOTIFICACION,MODULO,MENSAJE,LEIDA,CREADO_EN FROM APP_NOTIFICACION WHERE ID_USUARIO=? AND (?=0 OR ID_NOTIFICACION<?) ORDER BY ID_NOTIFICACION DESC FETCH FIRST 51 ROWS ONLY",
            (rs,n)->new Notice(rs.getLong(1),rs.getString(2),rs.getString(3),"S".equals(rs.getString(4)),rs.getTimestamp(5).toString()),user,before,before);
        long unread=jdbc.queryForObject("SELECT COUNT(*) FROM APP_NOTIFICACION WHERE ID_USUARIO=? AND LEIDA='N'",Long.class,user);
        return new Page(unread,rows.stream().limit(50).toList(),rows.size()>50);
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
    public record Notice(long id,String module,String message,boolean read,String createdAt) {}
    public record Page(long unread,List<Notice> items,boolean hasMore) {}
}
