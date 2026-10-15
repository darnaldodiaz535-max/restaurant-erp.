package cl.restaurante.api.health;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/perfil/vacaciones")
public class VacationController {
    private static final int DAYS_PER_COMPLETED_YEAR = 15;
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;

    public VacationController(JdbcTemplate jdbc, RoleAccessService access, ActivityService activity) {
        this.jdbc = jdbc;
        this.access = access;
        this.activity = activity;
    }

    @GetMapping
    public VacationSummary summary(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        Timestamp created = jdbc.queryForObject("SELECT CREADO_EN FROM APP_USUARIO WHERE ID_USUARIO=?", Timestamp.class, user.id());
        LocalDate start = created.toLocalDateTime().toLocalDate();
        LocalDate today = LocalDate.now();
        long years = Math.max(0, ChronoUnit.YEARS.between(start, today));
        BigDecimal earned = BigDecimal.valueOf(years * DAYS_PER_COMPLETED_YEAR);
        BigDecimal used = jdbc.queryForObject("SELECT NVL(SUM(DIAS),0) FROM APP_FERIADO_USO WHERE ID_EMPLEADO=?", BigDecimal.class, user.employeeId());
        List<VacationUse> uses = jdbc.query("SELECT ID_USO, FECHA_USO, DIAS, OBSERVACION FROM APP_FERIADO_USO WHERE ID_EMPLEADO=? ORDER BY FECHA_USO DESC, ID_USO DESC",
                (rs, row) -> new VacationUse(rs.getLong(1), rs.getDate(2).toLocalDate().toString(), rs.getBigDecimal(3), rs.getString(4)), user.employeeId());
        BigDecimal usedDays = used == null ? BigDecimal.ZERO : used;
        return new VacationSummary(start.toString(), ChronoUnit.DAYS.between(start, today), years, earned.stripTrailingZeros(),
                usedDays.stripTrailingZeros(), earned.subtract(usedDays).stripTrailingZeros(), uses);
    }

    @PostMapping("/usos")
    @Transactional
    public VacationUse register(@RequestBody VacationUseRequest request, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isOwner(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración puede registrar vacaciones.");
        if (request == null || request.employeeId() == null || request.date() == null || request.days() == null
                || request.days().signum() <= 0 || request.days().compareTo(BigDecimal.valueOf(30)) > 0
                || request.date().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica un trabajador, una fecha válida y entre 0,01 y 30 días.");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM APP_EMPLEADO WHERE ID_EMPLEADO=?", Integer.class, request.employeeId());
        if (exists == null || exists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el trabajador.");
        var key = new GeneratedKeyHolder();
        PreparedStatementCreator statement = connection -> {
            var ps = connection.prepareStatement("INSERT INTO APP_FERIADO_USO(ID_EMPLEADO,FECHA_USO,DIAS,OBSERVACION,REGISTRADO_POR) VALUES(?,?,?,?,?)", new String[] { "ID_USO" });
            ps.setLong(1, request.employeeId()); ps.setDate(2, java.sql.Date.valueOf(request.date())); ps.setBigDecimal(3, request.days());
            ps.setString(4, clean(request.note())); ps.setLong(5, user.id()); return ps;
        };
        jdbc.update(statement, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("Oracle no devolvió el identificador del registro.");
        activity.record(session, "REGISTRAR", "Vacaciones", "Se registró uso de " + request.days() + " días para trabajador #" + request.employeeId());
        return jdbc.queryForObject("SELECT ID_USO, FECHA_USO, DIAS, OBSERVACION FROM APP_FERIADO_USO WHERE ID_USO=?",
                (rs, row) -> new VacationUse(rs.getLong(1), rs.getDate(2).toLocalDate().toString(), rs.getBigDecimal(3), rs.getString(4)), id.longValue());
    }

    @DeleteMapping("/usos/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable long id, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isOwner(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración puede corregir el registro de vacaciones.");
        int changed = jdbc.update("DELETE FROM APP_FERIADO_USO WHERE ID_USO=?", id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el registro de vacaciones.");
        activity.record(session, "CORREGIR", "Vacaciones", "Se eliminó un registro de uso de vacaciones.");
        return ResponseEntity.noContent().build();
    }

    private String clean(String note) {
        if (note == null || note.isBlank()) return null;
        String value = note.trim();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    public record VacationSummary(String startDate, long serviceDays, long completedYears,
            BigDecimal accruedDays, BigDecimal usedDays, BigDecimal balanceDays, List<VacationUse> uses) {}
    public record VacationUse(long id, String date, BigDecimal days, String note) {}
    public record VacationUseRequest(Long employeeId, LocalDate date, BigDecimal days, String note) {}
}
