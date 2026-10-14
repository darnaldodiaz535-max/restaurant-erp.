package cl.restaurante.api.health;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/asistencia")
@CrossOrigin(origins = "*")
public class AttendanceController {
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final long MAX_PHOTO_BYTES = 4 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;

    public AttendanceController(JdbcTemplate jdbc, RoleAccessService access, ActivityService activity) {
        this.jdbc = jdbc;
        this.access = access;
        this.activity = activity;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        String rut = jdbc.query("SELECT RUT FROM APP_EMPLEADO WHERE ID_EMPLEADO=?", rs -> rs.next() ? rs.getString(1) : null, user.employeeId());
        return Map.of("nombre", user.name(), "rut", rut == null ? "" : rut);
    }

    @Transactional
    @PostMapping(value = "/marcar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> mark(@RequestParam String tipo, @RequestParam String rut,
                                  @RequestParam("foto") MultipartFile photo, HttpSession session) throws IOException {
        RoleAccessService.User user = access.current(session);
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES
                || photo.getContentType() == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(photo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser JPG, PNG o WebP y pesar hasta 4 MB.");
        }
        String action = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!Set.of("ENTRADA", "SALIDA").contains(action)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige Entrada o Salida.");
        String suppliedRut = normalizeRut(rut);
        String registeredRut = jdbc.query("SELECT RUT FROM APP_EMPLEADO WHERE ID_EMPLEADO=? AND ESTADO='ACTIVO'",
                rs -> rs.next() ? rs.getString(1) : null, user.employeeId());
        if (registeredRut == null || suppliedRut.isBlank() || !normalizeRut(registeredRut).equals(suppliedRut)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El RUT no coincide con tu cuenta activa. Pide a administración que revise tu registro.");
        }
        byte[] bytes = photo.getBytes();
        ImageUpload.validate(bytes, photo.getContentType());
        LocalDateTime now = LocalDateTime.now(CHILE);
        LocalDate today = now.toLocalDate();
        synchronized (this) {
            List<Long> rows = jdbc.query("SELECT ID_ASISTENCIA FROM APP_ASISTENCIA WHERE EMPLEADO_ID=? AND FECHA=? FOR UPDATE",
                    (rs, n) -> rs.getLong(1), user.employeeId(), java.sql.Date.valueOf(today));
            if ("ENTRADA".equals(action)) {
                if (!rows.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya registraste tu entrada hoy.");
                }
                jdbc.update("INSERT INTO APP_ASISTENCIA (EMPLEADO_ID,FECHA,HORA_ENTRADA,ESTADO,ENTRADA_EN,FOTO_ENTRADA,TIPO_FOTO_ENTRADA) VALUES (?,?,?,'PRESENTE',?,?,?)",
                        user.employeeId(), java.sql.Date.valueOf(today), now.format(HOUR), java.sql.Timestamp.valueOf(now), bytes, photo.getContentType());
            } else {
                List<Long> openRows = jdbc.query("SELECT ID_ASISTENCIA FROM APP_ASISTENCIA WHERE EMPLEADO_ID=? AND FECHA=? AND ENTRADA_EN IS NOT NULL AND SALIDA_EN IS NULL FOR UPDATE",
                        (rs, n) -> rs.getLong(1), user.employeeId(), java.sql.Date.valueOf(today));
                Long open = openRows.isEmpty() ? null : openRows.getLast();
                if (open == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay una entrada abierta hoy. Registra Entrada antes de Salida.");
                jdbc.update("UPDATE APP_ASISTENCIA SET HORA_SALIDA=?,SALIDA_EN=?,FOTO_SALIDA=?,TIPO_FOTO_SALIDA=? WHERE ID_ASISTENCIA=?",
                        now.format(HOUR), java.sql.Timestamp.valueOf(now), bytes, photo.getContentType(), open);
            }
        }
        activity.record(session, "MARCAR_" + action, "Asistencia", user.name() + " registró " + action.toLowerCase() + " a las " + now.format(HOUR));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "guardado", "tipo", action, "hora", now.format(HOUR)));
    }

    @GetMapping("/reporte")
    public Map<String, Object> report(@RequestParam(defaultValue = "mes") String periodo,
                                      @RequestParam(required = false) String fecha,
                                      @RequestParam(required = false) Long empleadoId,
                                      HttpSession session) {
        RoleAccessService.User user = access.current(session);
        boolean admin = isAdmin(user);
        LocalDate anchor = fecha == null || fecha.isBlank() ? LocalDate.now(CHILE) : LocalDate.parse(fecha);
        LocalDate from, to;
        switch (periodo.toLowerCase()) {
            case "dia" -> { from = anchor; to = anchor; }
            case "semana" -> { from = anchor.with(DayOfWeek.MONDAY); to = from.plusDays(6); }
            case "mes" -> { from = anchor.withDayOfMonth(1); to = YearMonth.from(anchor).atEndOfMonth(); }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período debe ser dia, semana o mes.");
        }
        Long selectedEmployee = admin ? empleadoId : user.employeeId();
        if (empleadoId != null && !admin) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración puede consultar a otra persona.");

        String filter = selectedEmployee == null ? "" : " AND E.ID_EMPLEADO=?";
        Object[] rowArgs = selectedEmployee == null
                ? new Object[]{java.sql.Date.valueOf(from), java.sql.Date.valueOf(to.plusDays(1))}
                : new Object[]{java.sql.Date.valueOf(from), java.sql.Date.valueOf(to.plusDays(1)), selectedEmployee};
        List<Map<String, Object>> rows = jdbc.query("SELECT A.ID_ASISTENCIA,E.ID_EMPLEADO,E.NOMBRE,E.RUT,A.FECHA,A.HORA_ENTRADA,A.HORA_SALIDA,A.ENTRADA_EN,A.SALIDA_EN,A.FOTO_ENTRADA,A.FOTO_SALIDA " +
                        "FROM APP_ASISTENCIA A JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=A.EMPLEADO_ID " +
                        "WHERE A.FECHA>=? AND A.FECHA<?" + filter + " ORDER BY E.NOMBRE,A.FECHA,A.ID_ASISTENCIA",
                (rs, n) -> Map.ofEntries(Map.entry("id", rs.getLong(1)), Map.entry("empleadoId", rs.getLong(2)),
                        Map.entry("nombre", rs.getString(3)), Map.entry("rut", rs.getString(4) == null ? "" : rs.getString(4)),
                        Map.entry("fecha", rs.getDate(5).toLocalDate().toString()), Map.entry("entrada", safe(rs.getString(6))),
                        Map.entry("salida", safe(rs.getString(7))), Map.entry("entradaFoto", rs.getBytes(10) != null), Map.entry("salidaFoto", rs.getBytes(11) != null)), rowArgs);

        List<Map<String, Object>> employees = admin ? jdbc.query("SELECT ID_EMPLEADO,NOMBRE,RUT FROM APP_EMPLEADO WHERE ESTADO='ACTIVO' ORDER BY NOMBRE",
                (rs, n) -> Map.of("id", rs.getLong(1), "nombre", rs.getString(2), "rut", rs.getString(3) == null ? "" : rs.getString(3))) : List.of();
        String summaryFilter = selectedEmployee == null ? "" : " AND E.ID_EMPLEADO=?";
        List<Map<String, Object>> summaries = jdbc.query("SELECT E.ID_EMPLEADO,E.NOMBRE,E.RUT," +
                        "(SELECT COUNT(DISTINCT TRUNC(T.FECHA)) FROM APP_TURNO T WHERE T.EMPLEADO_ID=E.ID_EMPLEADO AND T.FECHA>=? AND T.FECHA<?) PROGRAMADOS," +
                        "(SELECT COUNT(DISTINCT TRUNC(A.FECHA)) FROM APP_ASISTENCIA A WHERE A.EMPLEADO_ID=E.ID_EMPLEADO AND A.FECHA>=? AND A.FECHA<? AND A.ENTRADA_EN IS NOT NULL AND A.SALIDA_EN IS NOT NULL) COMPLETOS " +
                        "FROM APP_EMPLEADO E WHERE E.ESTADO='ACTIVO'" + summaryFilter + " ORDER BY E.NOMBRE",
                (rs,n)-> {
                    long scheduled=rs.getLong(4), complete=rs.getLong(5);
                    return Map.of("empleadoId",rs.getLong(1),"nombre",rs.getString(2),"rut",rs.getString(3)==null?"":rs.getString(3),
                            "turnosProgramados",scheduled,"turnosCompletos",complete,
                            "porcentaje",scheduled==0?"—":String.format(java.util.Locale.ROOT,"%.1f%%",100.0*complete/scheduled));
                }, selectedEmployee == null
                        ? new Object[]{java.sql.Date.valueOf(from),java.sql.Date.valueOf(to.plusDays(1)),java.sql.Date.valueOf(from),java.sql.Date.valueOf(to.plusDays(1))}
                        : new Object[]{java.sql.Date.valueOf(from),java.sql.Date.valueOf(to.plusDays(1)),java.sql.Date.valueOf(from),java.sql.Date.valueOf(to.plusDays(1)),selectedEmployee});
        return Map.of("periodo", periodo, "desde", from.toString(), "hasta", to.toString(), "marcas", rows, "resumen", summaries, "personas", employees);
    }

    @GetMapping("/{id}/foto/{tipo}")
    public ResponseEntity<byte[]> photo(@PathVariable long id, @PathVariable String tipo, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        boolean admin = isAdmin(user);
        if (!Set.of("entrada", "salida").contains(tipo.toLowerCase())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de foto inválido.");
        String column = tipo.equalsIgnoreCase("entrada") ? "FOTO_ENTRADA" : "FOTO_SALIDA";
        String typeColumn = tipo.equalsIgnoreCase("entrada") ? "TIPO_FOTO_ENTRADA" : "TIPO_FOTO_SALIDA";
        List<Photo> photos = jdbc.query("SELECT " + column + "," + typeColumn + ",EMPLEADO_ID FROM APP_ASISTENCIA WHERE ID_ASISTENCIA=?",
                (rs,n)->new Photo(rs.getBytes(1),rs.getString(2),rs.getLong(3)),id);
        if (photos.isEmpty() || photos.getFirst().bytes() == null) return ResponseEntity.notFound().build();
        Photo picture=photos.getFirst();
        if (!admin && picture.employeeId()!=user.employeeId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo puedes ver tus propias fotos.");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(picture.type())).header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(picture.bytes());
    }

    private boolean isAdmin(RoleAccessService.User user) { return Set.of("ADMIN", "EMPRESA").contains(user.role()); }
    private static String normalizeRut(String value) { return value == null ? "" : value.toUpperCase().replaceAll("[.\\-\\s]", ""); }
    private static String safe(String value) { return value == null ? "" : value; }
    private record Photo(byte[] bytes, String type, long employeeId) {}
}
