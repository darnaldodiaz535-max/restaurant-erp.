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
    private final EmployeeDeletionService deletion;

    public OracleConnectionController(JdbcTemplate jdbcTemplate, ActivityService activity, RoleAccessService access, EmployeeDeletionService deletion) {
        this.jdbcTemplate = jdbcTemplate;
        this.activity = activity;
        this.access = access;
        this.deletion = deletion;
    }

@GetMapping
public List<Employee> list(HttpSession session) {
    RoleAccessService.User user = access.current(session);
    String sql =
            "SELECT E.ID_EMPLEADO, E.NOMBRE, E.CARGO, E.AREA, E.RUT, E.ESTADO, U.ROL AS ROL_ACCESO, " +
                    "CASE WHEN U.ID_USUARIO IS NULL THEN 0 ELSE 1 END AS TIENE_CUENTA " +
                    "FROM APP_EMPLEADO E LEFT JOIN APP_USUARIO U ON U.ID_EMPLEADO = E.ID_EMPLEADO " +
                    "ORDER BY E.ID_EMPLEADO";
    return jdbcTemplate.query(sql,
            (rs, rowNum) -> new Employee(
                    rs.getLong("ID_EMPLEADO"),
                    rs.getString("NOMBRE"),
                    rs.getString("CARGO"),
                    rs.getString("AREA"),
                    rs.getString("RUT"),
                    "ACTIVO".equalsIgnoreCase(rs.getString("ESTADO")),
                    rs.getInt("TIENE_CUENTA") == 1,
                    rs.getString("ROL_ACCESO") == null ? null : RoleAccessService.normalize(rs.getString("ROL_ACCESO"))
            )
    );
}

   @PostMapping
public ResponseEntity<Map<String, String>> create(@RequestBody EmployeeRequest request, HttpSession session) {
    RoleAccessService.User user = access.current(session);
    if (!access.isOwner(user)) {
        return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para crear personal."));
    }
    if (!valid(request)) {
        return ResponseEntity.badRequest().body(Map.of("error", "Completa nombre, cargo y área."));
    }

    String status = request.active() == null || request.active() ? "ACTIVO" : "INACTIVO";
    String rut = request.rut() == null || request.rut().isBlank() ? null : request.rut().trim();

    jdbcTemplate.update(
            "INSERT INTO APP_EMPLEADO (NOMBRE, CARGO, AREA, RUT, ESTADO) VALUES (?, ?, ?, ?, ?)",
            request.name().trim(), request.role().trim(), request.area().trim(), rut, status
    );
    activity.record(session, "CREAR", "Personal", "Se agregó un trabajador");
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "guardado"));
}

   @PutMapping("/{id}")
public ResponseEntity<Map<String, String>> update(@PathVariable long id, @RequestBody EmployeeRequest request, HttpSession session) {
    RoleAccessService.User user = access.current(session);
    if (!access.isOwner(user)) {
        return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para modificar Personal."));
    }
    if (!valid(request)) {
        return ResponseEntity.badRequest().body(Map.of("error", "Completa nombre, cargo y área."));
    }

    String status = request.active() == null || request.active() ? "ACTIVO" : "INACTIVO";
    String rut = request.rut() == null || request.rut().isBlank() ? null : request.rut().trim();

    int changed = jdbcTemplate.update(
            "UPDATE APP_EMPLEADO SET NOMBRE = ?, CARGO = ?, AREA = ?, RUT = ?, ESTADO = ? WHERE ID_EMPLEADO = ?",
            request.name().trim(), request.role().trim(), request.area().trim(), rut, status, id
    );
    if (changed == 0) return ResponseEntity.notFound().build();

    activity.record(session, "ACTUALIZAR", "Personal", "Se actualizaron los datos de un trabajador");
    return ResponseEntity.ok(Map.of("status", "actualizado"));
}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id, HttpSession session) {
        deletion.delete(id, session);
        // DELETE is idempotent: a retry after a committed deletion is successful too.
        return ResponseEntity.noContent().build();
    }
    private boolean valid(EmployeeRequest request) {
        return request != null
                && request.name() != null && !request.name().isBlank()
                && request.role() != null && !request.role().isBlank()
                && request.area() != null && !request.area().isBlank();
    }
public record Employee(long id, String name, String role, String area, String rut, boolean active, boolean hasAccount, String accessRole) {}
public record EmployeeRequest(String name, String role, String area, String rut, Boolean active) {}
}





