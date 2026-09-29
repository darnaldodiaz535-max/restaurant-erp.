package cl.restaurante.api.health;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AreaService {
    public static final Set<String> AREAS = Set.of("COCINA", "SALON", "BARRA", "COPERIA");
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    public AreaService(JdbcTemplate jdbc, RoleAccessService access) { this.jdbc=jdbc; this.access=access; }
    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }
    public static String valid(String value) {
        String area=normalize(value);
        if (!AREAS.contains(area)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona Cocina, Salón, Barra o Copería.");
        return area;
    }
    // Column names are fixed by the application, never received from a request.
    public static String sql(String column) { return "TRANSLATE(UPPER(TRIM("+column+")), 'ÁÉÍÓÚÜ', 'AEIOUU')"; }
    public String employeeArea(long id) {
        return normalize(jdbc.queryForObject("SELECT AREA FROM APP_EMPLEADO WHERE ID_EMPLEADO=?", String.class, id));
    }
    public String requireView(HttpSession session, String value) {
        String area=valid(value);
        access.current(session);
        return area;
    }
}
