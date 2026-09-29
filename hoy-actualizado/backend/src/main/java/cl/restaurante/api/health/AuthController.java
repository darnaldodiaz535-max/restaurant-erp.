package cl.restaurante.api.health;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final int ITERATIONS = 180_000;
    private final JdbcTemplate jdbc;
    private final ActivityService activity;
    private final RoleAccessService access;
    private final SecureRandom random = new SecureRandom();

    public AuthController(JdbcTemplate jdbc, ActivityService activity, RoleAccessService access) { this.jdbc = jdbc; this.activity = activity; this.access = access; }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("setupRequired", jdbc.queryForObject("SELECT COUNT(*) FROM APP_USUARIO", Integer.class) == 0);
    }

    @GetMapping("/setup/employees")
    public List<EmployeeOption> setupEmployees() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM APP_USUARIO", Integer.class) != 0) {
            throw new IllegalStateException("La configuración inicial ya se completó.");
        }
        return jdbc.query("SELECT ID_EMPLEADO, NOMBRE, CARGO FROM APP_EMPLEADO ORDER BY NOMBRE",
                (rs, row) -> new EmployeeOption(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody SetupRequest request, HttpSession session) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM APP_USUARIO", Integer.class) != 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "La configuración inicial ya se completó."));
        }
        if (!valid(request.username(), request.password()) || request.employeeId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selecciona tu trabajador e ingresa usuario y contraseña (mínimo 10 caracteres)."));
        }
        try {
            jdbc.update("INSERT INTO APP_USUARIO (ID_EMPLEADO, USUARIO, CLAVE_HASH, ROL, ACTIVO) VALUES (?, ?, ?, 'EMPRESA', 'S')",
                    request.employeeId(), normalize(request.username()), hash(request.password()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo crear el administrador. Revisa que el trabajador no tenga una cuenta y que el usuario esté disponible."));
        }
        Long userId = jdbc.queryForObject("SELECT ID_USUARIO FROM APP_USUARIO WHERE USUARIO = ?", Long.class, normalize(request.username()));
        session.setAttribute("userId", userId);
        activity.record(session, "CREAR", "Accesos", "Se configuró la cuenta administradora");
        return ResponseEntity.status(HttpStatus.CREATED).body(profile(userId));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
        if (request == null || request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Escribe tu usuario y contraseña."));
        }
        List<LoginRow> rows = jdbc.query(
                "SELECT ID_USUARIO, CLAVE_HASH, ACTIVO FROM APP_USUARIO WHERE LOWER(USUARIO) = LOWER(?)",
                (rs, row) -> new LoginRow(rs.getLong(1), rs.getString(2), rs.getString(3)), request.username().trim());
        if (rows.isEmpty() || !"S".equalsIgnoreCase(rows.get(0).active()) || !matches(request.password(), rows.get(0).hash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Usuario o contraseña incorrectos."));
        }
        session.setAttribute("userId", rows.get(0).id());
        return ResponseEntity.ok(profile(rows.get(0).id()));
    }

    @GetMapping("/session")
    public ResponseEntity<?> current(HttpSession session) {
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? ResponseEntity.ok(profile(number.longValue())) : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Inicia sesión."));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("status", "sesión cerrada");
    }

    @PostMapping("/users")
    public ResponseEntity<?> createEmployeeUser(@RequestBody UserRequest request, HttpSession session) {
        if (!access.isOwner(access.current(session))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Solo el jefe de empresa puede crear accesos."));
        if (request == null || request.employeeId() == null || !valid(request.username(), request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa usuario y contraseña (mínimo 10 caracteres)."));
        }
        String requestedRole = request.role() == null ? "TRABAJADOR" : request.role().trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("ADMIN", "EMPRESA", "JEFE_SALON", "JEFE_COCINA", "JEFE_LOCAL", "TRABAJADOR", "EMPLEADO").contains(requestedRole)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selecciona un rol válido."));
        }
        String role = RoleAccessService.normalize(requestedRole);
        try {
            jdbc.update("INSERT INTO APP_USUARIO (ID_EMPLEADO, USUARIO, CLAVE_HASH, ROL, ACTIVO) VALUES (?, ?, ?, ?, 'S')",
                    request.employeeId(), normalize(request.username()), hash(request.password()), role);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo crear el acceso. Puede que el trabajador ya tenga uno o que el usuario esté ocupado."));
        }
        activity.record(session, "CREAR", "Accesos", "Se creó un acceso para el trabajador #" + request.employeeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "acceso creado"));
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody PasswordChangeRequest request, HttpSession session) {
        long userId = access.current(session).id();
        if (request == null || request.currentPassword() == null || request.currentPassword().isEmpty()
                || request.currentPassword().length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Escribe tu contraseña actual."));
        }
        String stored = jdbc.queryForObject("SELECT CLAVE_HASH FROM APP_USUARIO WHERE ID_USUARIO=?", String.class, userId);
        if (!matches(request.currentPassword(), stored)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña actual es incorrecta."));
        }
        if (request.newPassword() == null || request.newPassword().length() < 10 || request.newPassword().length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña debe tener entre 10 y 200 caracteres."));
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Las nuevas contraseñas no coinciden."));
        }
        if (request.newPassword().equals(request.currentPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña debe ser diferente de la actual."));
        }
        // La sesión determina la cuenta. Comparar el hash anterior evita sobrescribir cambios concurrentes.
        int changed = jdbc.update("UPDATE APP_USUARIO SET CLAVE_HASH=? WHERE ID_USUARIO=? AND CLAVE_HASH=? AND ACTIVO='S'",
                hash(request.newPassword()), userId, stored);
        if (changed != 1) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "La cuenta cambió mientras guardabas. Vuelve a iniciar sesión e inténtalo otra vez."));
        }
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword, String confirmPassword) {}

    private Profile profile(long id) {
        return jdbc.queryForObject(
                "SELECT U.ID_USUARIO, U.USUARIO, U.ROL, E.ID_EMPLEADO, E.NOMBRE, E.CARGO, E.AREA " +
                "FROM APP_USUARIO U JOIN APP_EMPLEADO E ON E.ID_EMPLEADO = U.ID_EMPLEADO WHERE U.ID_USUARIO = ?",
                (rs, row) -> new Profile(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)), id);
    }

    private boolean valid(String username, String password) {
        return username != null && username.trim().length() >= 3 && username.trim().length() <= 60
                && password != null && password.length() >= 10 && password.length() <= 200;
    }
    private String normalize(String username) { return username.trim().toLowerCase(java.util.Locale.ROOT); }

    private String hash(String password) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        byte[] digest = derive(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(digest);
    }

    private boolean matches(String password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, Integer.parseInt(parts[1])));
        } catch (RuntimeException e) { return false; }
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("No se pudo proteger la contraseña.", e); }
        finally { spec.clearPassword(); }
    }

    public record EmployeeOption(long id, String name, String role) {}
    public record SetupRequest(Long employeeId, String username, String password) {}
    public record LoginRequest(String username, String password) {}
    public record UserRequest(Long employeeId, String username, String password, String role) {}
    public record Profile(long id, String username, String role, long employeeId, String name, String position, String area) {}
    private record LoginRow(long id, String hash, String active) {}
}

