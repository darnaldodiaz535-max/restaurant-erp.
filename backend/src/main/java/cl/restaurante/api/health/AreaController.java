package cl.restaurante.api.health;

import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/areas")
public class AreaController {
    private final JdbcTemplate jdbc;
    private final AreaService areas;
    public AreaController(JdbcTemplate jdbc, AreaService areas) { this.jdbc=jdbc; this.areas=areas; }
    @GetMapping("/{area}/empleados")
    public List<Member> members(@PathVariable String area, HttpSession session) {
        String selected=areas.requireView(session,area);
        return jdbc.query("SELECT ID_EMPLEADO,NOMBRE,CARGO,ESTADO FROM APP_EMPLEADO WHERE "+AreaService.sql("AREA")+"=? ORDER BY NOMBRE",
            (rs,n)->new Member(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)), selected);
    }
    public record Member(long id,String name,String position,String status) {}
}
