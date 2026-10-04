package cl.restaurante.api.health;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/empleados")
@CrossOrigin(origins = "*")
public class OracleConnectionController {

    private final JdbcTemplate jdbcTemplate;
    private final ActivityService activity;
    private final RoleAccessService access;

    public OracleConnectionController(JdbcTemplate jdbcTemplate, ActivityService activity, RoleAccessService access) {
        this.jdbcTemplate = jdbcTemplate;
        this.activity = activity;
        this.access = access;
    }

    @GetMapping
    public List<Employee> list(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        String sql =
                "SELECT E.ID_EMPLEADO, E.NOMBRE, E.CARGO, E.AREA, E.ESTADO, " +
                        "CASE WHEN U.ID_USUARIO IS NULL THEN 0 ELSE 1 END AS TIENE_CUENTA " +
                        "FROM APP_EMPLEADO E LEFT JOIN APP_USUARIO U ON U.ID_EMPLEADO = E.ID_EMPLEADO " +
                        "ORDER BY E.ID_EMPLEADO";
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new Employee(
                        rs.getLong("ID_EMPLEADO"),
                        rs.getString("NOMBRE"),
                        rs.getString("CARGO"),
                        rs.getString("AREA"),
                        "ACTIVO".equalsIgnoreCase(rs.getString("ESTADO")),
                        rs.getInt("TIENE_CUENTA") == 1
                )
        );
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody EmployeeRequest request, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isOwner(user)) return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para crear personal."));
        if (!valid(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa nombre, cargo y área."));
        }
        String status = request.active() == null || request.active() ? "ACTIVO" : "INACTIVO";
        jdbcTemplate.update(
                "INSERT INTO APP_EMPLEADO (NOMBRE, CARGO, AREA, ESTADO) VALUES (?, ?, ?, ?)",
                request.name().trim(), request.role().trim(), request.area().trim(), status
        );
        activity.record(session, "CREAR", "Personal", "Se agregó un trabajador");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "guardado"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(@PathVariable long id, @RequestBody EmployeeRequest request, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isOwner(user)) return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para modificar Personal."));
        if (!valid(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa nombre, cargo y área."));
        }
        String status = request.active() == null || request.active() ? "ACTIVO" : "INACTIVO";
        int changed = jdbcTemplate.update(
                "UPDATE APP_EMPLEADO SET NOMBRE = ?, CARGO = ?, AREA = ?, ESTADO = ? WHERE ID_EMPLEADO = ?",
                request.name().trim(), request.role().trim(), request.area().trim(), status, id
        );
        if (changed == 0) return ResponseEntity.notFound().build();
        activity.record(session, "ACTUALIZAR", "Personal", "Se actualizaron los datos de un trabajador");
        return ResponseEntity.ok(Map.of("status", "actualizado"));
    }

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> delete(@PathVariable long id, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isOwner(user)) return ResponseEntity.status(403).body(Map.of("error", "Solo el jefe de empresa puede eliminar personal."));
        Object userId = session.getAttribute("userId");
        if (userId instanceof Number number) {
            Long linkedEmployeeId = jdbcTemplate.queryForObject(
                    "SELECT ID_EMPLEADO FROM APP_USUARIO WHERE ID_USUARIO = ?", Long.class, number.longValue());
            if (linkedEmployeeId != null && linkedEmployeeId == id) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "No puedes eliminar el trabajador asociado a la cuenta que está en uso."));
            }
        }
        jdbcTemplate.update("DELETE FROM APP_EVIDENCIA_TAREA WHERE ID_EMPLEADO=?", id);
        int changed = jdbcTemplate.update("DELETE FROM APP_EMPLEADO WHERE ID_EMPLEADO = ?", id);
        if (changed > 0) activity.record(session, "ELIMINAR", "Personal", "Se eliminó un trabajador");
        return changed == 0 ? ResponseEntity.notFound().build() : ResponseEntity.noContent().build();
    }
    private boolean valid(EmployeeRequest request) {
        return request != null
                && request.name() != null && !request.name().isBlank()
                && request.role() != null && !request.role().isBlank()
                && request.area() != null && !request.area().isBlank();
    }

    public record Employee(long id, String name, String role, String area, boolean active, boolean hasAccount) {}
    public record EmployeeRequest(String name, String role, String area, Boolean active) {}
}






