package cl.restaurante.api.health;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {
    private final JdbcTemplate jdbc;
    public ActivityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(HttpSession session, String action, String module, String detail) {
        String username = "Sistema";
        Object userId = session == null ? null : session.getAttribute("userId");
        if (userId instanceof Number number) {
            username = jdbc.queryForObject("SELECT USUARIO FROM APP_USUARIO WHERE ID_USUARIO = ?", String.class, number.longValue());
        }
        jdbc.update("INSERT INTO APP_ACTIVIDAD (USUARIO, ACCION, MODULO, DETALLE) VALUES (?, ?, ?, ?)", username, action, module, detail);
    }
}

