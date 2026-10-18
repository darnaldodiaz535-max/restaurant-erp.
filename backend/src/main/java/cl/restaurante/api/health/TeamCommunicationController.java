package cl.restaurante.api.health;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/comunicacion")
public class TeamCommunicationController {
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");
    private static final Set<String> REPORT_ROLES = Set.of(
            "ADMIN", "EMPRESA", "JEFE_SALON", "JEFE_COCINA", "JEFE_BARRA");
    private static final Map<String, String> AREA_LEADER_ROLE = Map.of(
            "SALON", "JEFE_SALON", "COCINA", "JEFE_COCINA",
            "BARRA", "JEFE_BARRA", "COPERIA", "JEFE_COPERIA");

    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;
    private final NotificationService notifications;

    public TeamCommunicationController(
            JdbcTemplate jdbc,
            RoleAccessService access,
            ActivityService activity,
            NotificationService notifications) {
        this.jdbc = jdbc;
        this.access = access;
        this.activity = activity;
        this.notifications = notifications;
    }

    @GetMapping("/destinatarios")
    public Map<String, Object> recipients(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        String area = employeeArea(user.employeeId());
        List<Person> recipients = targetAccounts(user);
        List<Person> supervisors = null;
        boolean mayRate = access.isWorker(user) && area != null;
        if (mayRate) supervisors = supervisors(area);
        Integer submittedThisMonth = mayRate
                ? jdbc.queryForObject("SELECT COUNT(*) FROM APP_EVAL_JEFATURA WHERE EVALUADOR_USUARIO=? AND AREA=? AND PERIODO=?",
                        Integer.class, user.id(), area, YearMonth.now(CHILE).toString())
                : 0;
        return Map.of(
                "area", area == null ? "" : area,
                "destinatarios", recipients,
                "jefaturasEvaluables", supervisors == null ? List.of() : supervisors,
                "puedeReportar", REPORT_ROLES.contains(user.role()),
                "puedeEnviarInquietud", !recipients.isEmpty(),
                "puedeEvaluar", mayRate,
                "evaluacionEnviada", submittedThisMonth != null && submittedThisMonth > 0,
                "periodoEvaluacion", YearMonth.now(CHILE).toString());
    }

    @GetMapping("/reportes")
    public List<DailyReport> reports(HttpSession session) {
        requireReportRole(session);
        purgeExpiredReports();
        return jdbc.query("""
                SELECT R.ID_REPORTE_DIARIO, R.AREA, R.FECHA_REPORTE, R.TITULO,
                       R.DETALLE, E.NOMBRE, R.CREADO_EN
                  FROM APP_REPORTE_DIARIO R
                  JOIN APP_USUARIO U ON U.ID_USUARIO=R.AUTOR_USUARIO
                  JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=U.ID_EMPLEADO
                 WHERE R.CREADO_EN >= SYSTIMESTAMP - INTERVAL '15' DAY
                 ORDER BY R.CREADO_EN DESC
                """, (rs, row) -> new DailyReport(
                rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate().toString(),
                rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getTimestamp(7).toLocalDateTime().toString()));
    }

    @PostMapping("/reportes")
    @Transactional
    public Map<String, Object> createReport(@RequestBody ReportRequest request, HttpSession session) {
        RoleAccessService.User user = requireReportRole(session);
        String area = reportArea(user, request == null ? null : request.area());
        String title = clean(request == null ? null : request.titulo());
        String detail = clean(request == null ? null : request.detalle());
        if (title.isBlank() || title.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un título de hasta 160 caracteres.");
        }
        if (detail.isBlank() || detail.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el reporte diario (máximo 4.000 caracteres).");
        }
        purgeExpiredReports();
        jdbc.update("""
                INSERT INTO APP_REPORTE_DIARIO
                    (AUTOR_USUARIO, AREA, FECHA_REPORTE, TITULO, DETALLE)
                VALUES (?, ?, ?, ?, ?)
                """, user.id(), area, java.sql.Date.valueOf(LocalDate.now(CHILE)), title, detail);
        activity.record(session, "CREAR", "Reportes", "Se guardó un reporte diario de " + area);
        return Map.of("status", "guardado");
    }

    @GetMapping("/inquietudes")
    public List<Concern> concerns(
            @RequestParam(defaultValue = "todas") String vista,
            HttpSession session) {
        long userId = access.current(session).id();
        String normalizedView = clean(vista).toLowerCase();
        if (!Set.of("todas", "enviadas", "recibidas").contains(normalizedView)) normalizedView = "todas";
        String filter = switch (normalizedView) {
            case "enviadas" -> "C.AUTOR_USUARIO=?";
            case "recibidas" -> "C.DESTINATARIO_USUARIO=?";
            default -> "(C.AUTOR_USUARIO=? OR C.DESTINATARIO_USUARIO=?)";
        };
        Object[] args = normalizedView.equals("todas")
                ? new Object[]{userId, userId} : new Object[]{userId};
        return jdbc.query("""
                SELECT C.ID_COMUNICACION, C.AREA, C.CATEGORIA, C.ASUNTO, C.MENSAJE,
                       C.ESTADO, C.CREADO_EN, C.AUTOR_USUARIO, C.DESTINATARIO_USUARIO,
                       EA.NOMBRE, ER.NOMBRE
                  FROM APP_COMUNICACION C
                  JOIN APP_USUARIO UA ON UA.ID_USUARIO=C.AUTOR_USUARIO
                  JOIN APP_EMPLEADO EA ON EA.ID_EMPLEADO=UA.ID_EMPLEADO
                  JOIN APP_USUARIO UR ON UR.ID_USUARIO=C.DESTINATARIO_USUARIO
                  JOIN APP_EMPLEADO ER ON ER.ID_EMPLEADO=UR.ID_EMPLEADO
                 WHERE """ + filter + " ORDER BY C.CREADO_EN DESC",
                (rs, row) -> new Concern(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime().toString(),
                        rs.getLong(8) == userId, rs.getLong(9) == userId, rs.getString(10), rs.getString(11)),
                args);
    }

    @PostMapping("/inquietudes")
    @Transactional
    public Map<String, Object> createConcern(@RequestBody ConcernRequest request, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (request == null || request.destinatarioUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona a quién enviar el mensaje.");
        }
        List<Person> allowed = targetAccounts(user);
        Person receiver = allowed.stream()
                .filter(candidate -> candidate.id() == request.destinatarioUsuario())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "El destinatario no corresponde a la ruta de comunicación de tu área."));
        String subject = clean(request.asunto());
        String message = clean(request.mensaje());
        String category = clean(request.categoria()).toUpperCase();
        if (!Set.of("INQUIETUD", "QUEJA", "SUGERENCIA", "SITUACION").contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un tipo de comunicación válido.");
        }
        if (subject.isBlank() || subject.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un asunto de hasta 160 caracteres.");
        }
        if (message.isBlank() || message.length() > 3000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el mensaje (máximo 3.000 caracteres).");
        }
        String area = employeeArea(user.employeeId());
        if (area == null) area = "GENERAL";
        jdbc.update("""
                INSERT INTO APP_COMUNICACION
                    (AUTOR_USUARIO, DESTINATARIO_USUARIO, AREA, CATEGORIA, ASUNTO, MENSAJE)
                VALUES (?, ?, ?, ?, ?, ?)
                """, user.id(), receiver.id(), area, category, subject, message);
        notifications.userWithDetail(receiver.id(), "Comunicación del equipo",
                "Nuevo mensaje de " + user.name() + ": " + subject,
                category + " · " + area + "\n" + message);
        activity.record(session, "CREAR", "Comunicación del equipo", "Se envió una " + category.toLowerCase() + " a " + receiver.name());
        return Map.of("status", "enviado");
    }

    @PutMapping("/inquietudes/{id}/estado")
    @Transactional
    public Map<String, Object> updateConcernStatus(
            @PathVariable long id,
            @RequestBody ConcernStatus request,
            HttpSession session) {
        long userId = access.current(session).id();
        String status = clean(request == null ? null : request.estado()).toUpperCase();
        if (!Set.of("EN_REVISION", "RESPONDIDA", "CERRADA").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un estado válido.");
        }
        int updated = jdbc.update("""
                UPDATE APP_COMUNICACION
                   SET ESTADO=?, ACTUALIZADO_EN=SYSTIMESTAMP
                 WHERE ID_COMUNICACION=? AND DESTINATARIO_USUARIO=?
                """, status, id, userId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró un mensaje recibido por tu cuenta.");
        return Map.of("status", "actualizado", "estado", status);
    }

    @PostMapping("/evaluaciones")
    @Transactional
    public Map<String, Object> rateSupervisor(@RequestBody EvaluationRequest request, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isWorker(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La evaluación está disponible para trabajadores.");
        String area = employeeArea(user.employeeId());
        if (area == null || !AREA_LEADER_ROLE.containsKey(area)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu ficha no tiene un área con jefatura evaluable.");
        }
        if (request == null || request.jefeUsuario() == null || request.puntaje() < 1 || request.puntaje() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una jefatura y una calificación de 1 a 5.");
        }
        boolean validBoss = supervisors(area).stream().anyMatch(person -> person.id() == request.jefeUsuario());
        if (!validBoss) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes evaluar a la jefatura de tu área.");
        String period = YearMonth.now(CHILE).toString();
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM APP_EVAL_JEFATURA
                 WHERE EVALUADOR_USUARIO=? AND AREA=? AND PERIODO=?
                """, Integer.class, user.id(), area, period);
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya enviaste tu evaluación de esta área durante este mes.");
        }
        jdbc.update("""
                INSERT INTO APP_EVAL_JEFATURA
                    (EVALUADOR_USUARIO, EVALUADO_USUARIO, AREA, PERIODO, PUNTAJE, COMENTARIO)
                VALUES (?, ?, ?, ?, ?, ?)
                """, user.id(), request.jefeUsuario(), area, period, request.puntaje(), null);
        activity.record(session, "EVALUAR", "Comunicación del equipo", "Se registró evaluación mensual de jefatura en " + area);
        return Map.of("status", "guardada", "periodo", period);
    }

    @GetMapping("/evaluaciones/resumen")
    public List<EvaluationSummary> evaluationSummary(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!Set.of("ADMIN", "EMPRESA").contains(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración puede consultar el resumen de evaluaciones.");
        }
        return jdbc.query("""
                SELECT V.AREA, U.ID_USUARIO, E.NOMBRE, V.PERIODO,
                       COUNT(*) CANTIDAD, ROUND(AVG(V.PUNTAJE), 2) PROMEDIO
                  FROM APP_EVAL_JEFATURA V
                  JOIN APP_USUARIO U ON U.ID_USUARIO=V.EVALUADO_USUARIO
                  JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=U.ID_EMPLEADO
                 WHERE V.PERIODO=?
                 GROUP BY V.AREA, U.ID_USUARIO, E.NOMBRE, V.PERIODO
                 ORDER BY V.AREA, E.NOMBRE
                """, (rs, row) -> new EvaluationSummary(
                rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getInt(5), rs.getBigDecimal(6)), YearMonth.now(CHILE).toString());
    }

    private RoleAccessService.User requireReportRole(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!REPORT_ROLES.contains(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Los reportes diarios están disponibles para administración y las jefaturas de salón, cocina y barra.");
        }
        return user;
    }

    private String reportArea(RoleAccessService.User user, String requested) {
        String assigned = switch (user.role()) {
            case "JEFE_SALON" -> "SALON";
            case "JEFE_COCINA" -> "COCINA";
            case "JEFE_BARRA" -> "BARRA";
            default -> null;
        };
        if (assigned != null) return assigned;
        String area = clean(requested).toUpperCase();
        if (!Set.of("SALON", "COCINA", "BARRA").contains(area)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona Salón, Cocina o Barra para el reporte.");
        }
        return area;
    }

    private List<Person> targetAccounts(RoleAccessService.User user) {
        String role = user.role();
        if (access.isWorker(user)) {
            String area = employeeArea(user.employeeId());
            String targetRole = AREA_LEADER_ROLE.get(area);
            if (targetRole == null) return List.of();
            return findUsers("U.ROL=? AND " + AreaService.sql("E.AREA") + "=?", targetRole, area);
        }
        if (Set.of("JEFE_SALON", "JEFE_COCINA", "JEFE_BARRA", "JEFE_COPERIA", "JEFE_LOCAL").contains(role)) {
            return findUsers("U.ROL IN ('ADMIN','EMPRESA')");
        }
        return List.of();
    }

    private List<Person> supervisors(String area) {
        String role = AREA_LEADER_ROLE.get(area);
        return role == null ? List.of() : findUsers("U.ROL=? AND " + AreaService.sql("E.AREA") + "=?", role, area);
    }

    private List<Person> findUsers(String clause, Object... args) {
        return jdbc.query("""
                SELECT U.ID_USUARIO, E.NOMBRE, U.ROL
                  FROM APP_USUARIO U JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=U.ID_EMPLEADO
                 WHERE U.ACTIVO='S' AND UPPER(E.ESTADO)='ACTIVO' """ + " AND " + clause + " ORDER BY E.NOMBRE",
                (rs, row) -> new Person(rs.getLong(1), rs.getString(2), rs.getString(3)), args);
    }

    private String employeeArea(long employeeId) {
        String raw = jdbc.query("SELECT AREA FROM APP_EMPLEADO WHERE ID_EMPLEADO=?",
                rs -> rs.next() ? rs.getString(1) : null, employeeId);
        return RoleAccessService.normalizeScheduleArea(raw);
    }

    private void purgeExpiredReports() {
        jdbc.update("DELETE FROM APP_REPORTE_DIARIO WHERE CREADO_EN < SYSTIMESTAMP - INTERVAL '15' DAY");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record Person(long id, String name, String role) {}
    public record DailyReport(long id, String area, String fecha, String titulo, String detalle, String autor, String creadoEn) {}
    public record ReportRequest(String area, String titulo, String detalle) {}
    public record Concern(long id, String area, String categoria, String asunto, String mensaje,
                          String estado, String creadoEn, boolean enviadoPorMi, boolean recibidoPorMi,
                          String autor, String destinatario) {}
    public record ConcernRequest(Long destinatarioUsuario, String categoria, String asunto, String mensaje) {}
    public record ConcernStatus(String estado) {}
    public record EvaluationRequest(Long jefeUsuario, int puntaje, String comentario) {}
    public record EvaluationSummary(String area, long jefeUsuario, String jefe, String periodo,
                                    int evaluaciones, java.math.BigDecimal promedio) {}
}
