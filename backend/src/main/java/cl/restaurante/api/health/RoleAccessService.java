package cl.restaurante.api.health;

import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoleAccessService {
    private static final Set<String> KITCHEN_MODULES = Set.of(
            "tareas-diarias", "inventario", "cocina", "produccion", "recetas",
            "limpieza", "control-sanitario", "compras", "mermas", "incidencias", "asistencia", "turnos");
    private final JdbcTemplate jdbc;

    public RoleAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public User current(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("userId");
        if (!(value instanceof Number id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        var users = jdbc.query("SELECT U.ID_USUARIO, U.USUARIO, U.ROL, E.ID_EMPLEADO, E.NOMBRE " +
                        "FROM APP_USUARIO U JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=U.ID_EMPLEADO WHERE U.ID_USUARIO=? AND U.ACTIVO='S'",
                (rs, row) -> new User(rs.getLong(1), rs.getString(2), normalize(rs.getString(3)), rs.getLong(4), rs.getString(5)), id.longValue());
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta ya no está activa. Inicia sesión.");
        return users.getFirst();
    }

    public boolean isOwner(User user) { return Set.of("ADMIN", "EMPRESA", "JEFE_SALON", "JEFE_COCINA", "JEFE_LOCAL").contains(user.role()); }
    public boolean canManageAttendanceSchedule(User user) { return isScheduleAdmin(user) || scheduleArea(user.role()) != null; }
    public boolean isScheduleAdmin(User user) { return Set.of("ADMIN", "EMPRESA").contains(user.role()); }
    public String scheduleArea(User user) { return scheduleArea(user.role()); }
    private static String scheduleArea(String role) { return switch (role) {
        case "JEFE_SALON" -> "SALON"; case "JEFE_COCINA" -> "COCINA";
        case "JEFE_BARRA" -> "BARRA"; case "JEFE_COPERIA" -> "COPERIA"; default -> null;
    }; }
    public static String normalizeScheduleArea(String value) {
        String area = java.text.Normalizer.normalize(value == null ? "" : value.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
        if (area.contains("COCINA")) return "COCINA";
        if (area.contains("SALON") || area.contains("SALA")) return "SALON";
        if (area.contains("BARRA")) return "BARRA";
        if (area.contains("COPER")) return "COPERIA";
        return null;
    }
    public boolean isSalon(User user) { return "JEFE_SALON".equals(user.role()); }
    public boolean isKitchen(User user) { return "JEFE_COCINA".equals(user.role()); }
    public boolean isWorker(User user) { return Set.of("TRABAJADOR", "EMPLEADO").contains(user.role()); }
    public boolean isManager(User user) { return isOwner(user) || isSalon(user) || isKitchen(user); }

    public void requireOwner(HttpSession session) {
        if (!isOwner(current(session))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el administrador de empresa puede realizar esta operación.");
    }

    public void requireModuleView(HttpSession session, String module) { current(session); }

    public void requireModuleEdit(HttpSession session, String module, boolean deleting) { requireOwner(session); }

    public void requireKitchenEmployee(long employeeId) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM APP_EMPLEADO WHERE ID_EMPLEADO=? AND UPPER(AREA) LIKE '%COCINA%'", Integer.class, employeeId);
        if (found == null || found == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El jefe de cocina solo puede asignar trabajadores del área de cocina.");
    }

    public static String normalize(String role) {
        String known = recognizedRole(role);
        return known == null ? "TRABAJADOR" : known;
    }

    public static String recognizedRole(String role) {
        if (role == null) return null;
        String value = java.text.Normalizer.normalize(role, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]+", "_");
        return switch (value) {
            case "ADMIN", "EMPRESA", "JEFE_SALON", "JEFE_COCINA", "JEFE_BARRA", "JEFE_COPERIA", "JEFE_LOCAL", "TRABAJADOR", "EMPLEADO" -> value;
            case "ADMINISTRADOR" -> "ADMIN";
            case "JEFE_DE_SALON", "ENCARGADO_DE_SALON", "ENCARGADO_SALON" -> "JEFE_SALON";
            case "JEFE_DE_COCINA", "ENCARGADO_DE_COCINA", "ENCARGADO_COCINA" -> "JEFE_COCINA";
            case "JEFE_DE_BARRA", "ENCARGADO_DE_BARRA", "ENCARGADO_BARRA" -> "JEFE_BARRA";
            case "JEFE_DE_COPERIA", "ENCARGADO_DE_COPERIA", "ENCARGADO_COPERIA" -> "JEFE_COPERIA";
            case "JEFE_DE_LOCAL" -> "JEFE_LOCAL";
            case "JEFE_DE_EMPRESA" -> "EMPRESA";
            default -> null;
        };
    }

    public record User(long id, String username, String role, long employeeId, String name) {}
}
