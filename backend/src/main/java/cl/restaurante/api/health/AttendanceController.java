package cl.restaurante.api.health;

import java.io.IOException;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private static final SecureRandom QR_RANDOM = new SecureRandom();
    private static final long QR_LIFETIME_MS = 60_000;
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;
    private final AttendanceScheduleService schedules;

    public AttendanceController(JdbcTemplate jdbc, RoleAccessService access, ActivityService activity, AttendanceScheduleService schedules) {
        this.jdbc = jdbc;
        this.access = access;
        this.activity = activity;
        this.schedules = schedules;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        String rut = jdbc.query("SELECT RUT FROM APP_EMPLEADO WHERE ID_EMPLEADO=?", rs -> rs.next() ? rs.getString(1) : null, user.employeeId());
        return Map.of("nombre", user.name(), "rut", rut == null ? "" : rut);
    }

    @GetMapping("/qr/config")
    public Map<String, Object> qrConfig(HttpSession session) {
        requireAttendanceAdmin(session);
        boolean qrTableReady = qrTableReady();
        QrSettings settings = qrTableReady ? qrSettings() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", settings != null);
        result.put("gpsSchemaReady", gpsColumnsReady());
        result.put("qrTableReady", qrTableReady);
        result.put("latitude", settings == null ? "" : settings.latitude());
        result.put("longitude", settings == null ? "" : settings.longitude());
        result.put("radiusMeters", settings == null ? 100 : settings.radiusMeters());
        return result;
    }

    @PostMapping("/qr/config")
    public Map<String, Object> saveQrConfig(@RequestBody AttendanceLocation location, HttpSession session) {
        requireAttendanceAdmin(session);
        if (!qrTableReady()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero ejecuta la migración 10_asistencia_qr_ubicacion.sql en Oracle.");
        if (location == null || !Double.isFinite(location.latitude()) || !Double.isFinite(location.longitude())
                || location.latitude() < -90 || location.latitude() > 90
                || location.longitude() < -180 || location.longitude() > 180
                || location.radiusMeters() < 20 || location.radiusMeters() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa las coordenadas y usa un radio entre 20 y 1.000 metros.");
        }
        jdbc.update("MERGE INTO APP_ASISTENCIA_QR C USING (SELECT 1 ID_CONFIG, ? LATITUD, ? LONGITUD, ? RADIO_METROS FROM DUAL) S ON (C.ID_CONFIG=S.ID_CONFIG) " +
                        "WHEN MATCHED THEN UPDATE SET C.LATITUD=S.LATITUD,C.LONGITUD=S.LONGITUD,C.RADIO_METROS=S.RADIO_METROS,C.QR_ACTUAL=NULL,C.QR_VENCE=NULL,C.QR_ANTERIOR=NULL,C.QR_ANTERIOR_VENCE=NULL " +
                        "WHEN NOT MATCHED THEN INSERT (ID_CONFIG,LATITUD,LONGITUD,RADIO_METROS) VALUES (S.ID_CONFIG,S.LATITUD,S.LONGITUD,S.RADIO_METROS)",
                location.latitude(), location.longitude(), location.radiusMeters());
        return qrConfig(session);
    }

    @GetMapping("/qr/actual")
    public ResponseEntity<Map<String, Object>> currentQr(HttpSession session) {
        requireAttendanceAdmin(session);
        if (!qrTableReady()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero ejecuta la migración 10_asistencia_qr_ubicacion.sql en Oracle.");
        QrSettings settings = qrSettings();
        if (settings == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero configura la ubicación y el radio del local en Administración.");
        long now = System.currentTimeMillis();
        long expires = settings.currentExpires();
        String token = settings.currentToken();
        if (token == null || expires <= now + 20_000) {
            String previous = token;
            String previousExpires = expires > now ? Long.toString(expires) : "0";
            byte[] entropy = new byte[32]; QR_RANDOM.nextBytes(entropy);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
            expires = now + QR_LIFETIME_MS;
            jdbc.update("UPDATE APP_ASISTENCIA_QR SET QR_ANTERIOR=?,QR_ANTERIOR_VENCE=?,QR_ACTUAL=?,QR_VENCE=? WHERE ID_CONFIG=1",
                    previous, parseLong(previousExpires), token, expires);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", token, "expiresAt", expires, "refreshInSeconds", 15));
    }

    @Transactional
    @PostMapping(value = "/marcar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> mark(@RequestParam String tipo, @RequestParam String rut,
                                  @RequestParam("foto") MultipartFile photo, @RequestParam String qrToken,
                                  @RequestParam double latitude, @RequestParam double longitude,
                                  @RequestParam double accuracy, HttpSession session) throws IOException {
        RoleAccessService.User user = access.current(session);
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES
                || photo.getContentType() == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(photo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser JPG, PNG o WebP y pesar hasta 4 MB.");
        }
        String action = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!Set.of("ENTRADA", "SALIDA").contains(action)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige Entrada o Salida.");
        if (!gpsColumnsReady() || !qrTableReady()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Falta aplicar la migración 10_asistencia_qr_ubicacion.sql en Oracle. Pide a administración que la ejecute y vuelve a intentar.");
        double distance = validateQrAndLocation(qrToken, latitude, longitude, accuracy);
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
                jdbc.update("INSERT INTO APP_ASISTENCIA (EMPLEADO_ID,FECHA,HORA_ENTRADA,ESTADO,ENTRADA_EN,FOTO_ENTRADA,TIPO_FOTO_ENTRADA,GPS_ENTRADA_LAT,GPS_ENTRADA_LON,GPS_ENTRADA_PRECISION,GPS_ENTRADA_DISTANCIA) VALUES (?,?,?,'PRESENTE',?,?,?,?,?,?,?)",
                        user.employeeId(), java.sql.Date.valueOf(today), now.format(HOUR), java.sql.Timestamp.valueOf(now), bytes, photo.getContentType(), latitude, longitude, accuracy, distance);
            } else {
                List<Long> openRows = jdbc.query("SELECT ID_ASISTENCIA FROM APP_ASISTENCIA WHERE EMPLEADO_ID=? AND FECHA=? AND ENTRADA_EN IS NOT NULL AND SALIDA_EN IS NULL FOR UPDATE",
                        (rs, n) -> rs.getLong(1), user.employeeId(), java.sql.Date.valueOf(today));
                Long open = openRows.isEmpty() ? null : openRows.getLast();
                if (open == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay una entrada abierta hoy. Registra Entrada antes de Salida.");
                jdbc.update("UPDATE APP_ASISTENCIA SET HORA_SALIDA=?,SALIDA_EN=?,FOTO_SALIDA=?,TIPO_FOTO_SALIDA=?,GPS_SALIDA_LAT=?,GPS_SALIDA_LON=?,GPS_SALIDA_PRECISION=?,GPS_SALIDA_DISTANCIA=? WHERE ID_ASISTENCIA=?",
                        now.format(HOUR), java.sql.Timestamp.valueOf(now), bytes, photo.getContentType(), latitude, longitude, accuracy, distance, open);
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
        // Administrators can request all staff by leaving empleadoId unset.
        // Avoid ternary unboxing of null when the other branch is primitive long.
        Long selectedEmployee;
        if (admin) selectedEmployee = empleadoId;
        else selectedEmployee = Long.valueOf(user.employeeId());
        if (empleadoId != null && !admin) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración puede consultar a otra persona.");

        String filter = selectedEmployee == null ? "" : " AND E.ID_EMPLEADO=?";
        boolean gpsReady = gpsColumnsReady();
        String gpsColumns = gpsReady ? ",A.GPS_ENTRADA_LAT,A.GPS_ENTRADA_LON,A.GPS_ENTRADA_PRECISION,A.GPS_ENTRADA_DISTANCIA,A.GPS_SALIDA_LAT,A.GPS_SALIDA_LON,A.GPS_SALIDA_PRECISION,A.GPS_SALIDA_DISTANCIA " : " ";
        Object[] rowArgs = selectedEmployee == null
                ? new Object[]{java.sql.Date.valueOf(from), java.sql.Date.valueOf(to.plusDays(1))}
                : new Object[]{java.sql.Date.valueOf(from), java.sql.Date.valueOf(to.plusDays(1)), selectedEmployee};
        List<Map<String, Object>> rows = jdbc.query("SELECT A.ID_ASISTENCIA,E.ID_EMPLEADO,E.NOMBRE,E.RUT,A.FECHA,A.HORA_ENTRADA,A.HORA_SALIDA,A.ENTRADA_EN,A.SALIDA_EN,A.FOTO_ENTRADA,A.FOTO_SALIDA" + gpsColumns +
                        "FROM APP_ASISTENCIA A JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=A.EMPLEADO_ID " +
                        "WHERE A.FECHA>=? AND A.FECHA<?" + filter + " ORDER BY E.NOMBRE,A.FECHA,A.ID_ASISTENCIA",
                (rs, n) -> {
                    String entry = safe(rs.getString(6));
                    String exit = safe(rs.getString(7));
                    String hours = workedHours(entry, exit, rs.getTimestamp(8), rs.getTimestamp(9));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong(1)); row.put("empleadoId", rs.getLong(2));
                    row.put("nombre", rs.getString(3)); row.put("rut", rs.getString(4) == null ? "" : rs.getString(4));
                    row.put("fecha", rs.getDate(5).toLocalDate().toString()); row.put("entrada", entry);
                    row.put("salida", exit); row.put("horasTrabajadas", hours);
                    row.put("entradaFoto", rs.getBytes(10) != null); row.put("salidaFoto", rs.getBytes(11) != null);
                    if (admin && gpsReady) {
                        row.put("entradaLat", safeNumber(rs.getObject(12))); row.put("entradaLon", safeNumber(rs.getObject(13)));
                        row.put("entradaPrecision", safeNumber(rs.getObject(14))); row.put("entradaDistancia", safeNumber(rs.getObject(15)));
                        row.put("salidaLat", safeNumber(rs.getObject(16))); row.put("salidaLon", safeNumber(rs.getObject(17)));
                        row.put("salidaPrecision", safeNumber(rs.getObject(18))); row.put("salidaDistancia", safeNumber(rs.getObject(19)));
                    } else if (admin) {
                        row.put("entradaLat", null); row.put("entradaLon", null); row.put("entradaPrecision", null); row.put("entradaDistancia", null);
                        row.put("salidaLat", null); row.put("salidaLon", null); row.put("salidaPrecision", null); row.put("salidaDistancia", null);
                    }
                    return row;
                }, rowArgs);

        if ("ADMIN".equals(user.role())) schedules.addScheduleDetails(rows, from, to, selectedEmployee);

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
        if ("ADMIN".equals(user.role()) && schedules.schemaReady()) {
            Map<Long, long[]> totals = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                if (row.get("turnoProgramado") == null || "LIBRE".equals(row.get("turnoProgramado"))) continue;
                if (LocalDate.parse(safeObject(row.get("fecha"))).isAfter(LocalDate.now(CHILE))) continue;
                long employeeId = ((Number) row.get("empleadoId")).longValue();
                long[] counts = totals.computeIfAbsent(employeeId, ignored -> new long[2]); counts[0]++;
                if (!safeObject(row.get("entrada")).isBlank() && !safeObject(row.get("salida")).isBlank()) counts[1]++;
            }
            List<Map<String, Object>> scheduleSummaries = new java.util.ArrayList<>();
            for (Map<String, Object> summary : summaries) {
                long employeeId = ((Number) summary.get("empleadoId")).longValue(); long[] counts = totals.getOrDefault(employeeId, new long[2]);
                Map<String, Object> updated = new LinkedHashMap<>(summary);
                updated.put("turnosProgramados", counts[0]); updated.put("turnosCompletos", counts[1]);
                updated.put("porcentaje", counts[0] == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * counts[1] / counts[0]));
                scheduleSummaries.add(updated);
            }
            summaries = scheduleSummaries;
        }
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
    private void requireAttendanceAdmin(HttpSession session) {
        if (!"ADMIN".equals(access.current(session).role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el administrador puede mostrar o configurar el QR de asistencia.");
    }
    private QrSettings qrSettings() {
        List<QrSettings> settings = jdbc.query("SELECT LATITUD,LONGITUD,RADIO_METROS,QR_ACTUAL,QR_VENCE,QR_ANTERIOR,QR_ANTERIOR_VENCE FROM APP_ASISTENCIA_QR WHERE ID_CONFIG=1",
                (rs, row) -> new QrSettings(rs.getDouble(1), rs.getDouble(2), rs.getInt(3), rs.getString(4), rs.getLong(5), rs.getString(6), rs.getLong(7)));
        return settings.isEmpty() ? null : settings.getFirst();
    }
    private boolean gpsColumnsReady() {
        return Boolean.TRUE.equals(jdbc.execute((Connection connection) -> {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "APP_ASISTENCIA", "GPS_ENTRADA_LAT")) {
                return columns.next();
            }
        }));
    }
    private boolean qrTableReady() {
        return Boolean.TRUE.equals(jdbc.execute((Connection connection) -> {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "APP_ASISTENCIA_QR", new String[]{"TABLE"})) {
                return tables.next();
            }
        }));
    }
    private double validateQrAndLocation(String suppliedToken, double latitude, double longitude, double accuracy) {
        QrSettings settings = qrSettings();
        if (settings == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Administración todavía no configura la ubicación del local.");
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || !Double.isFinite(accuracy)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || accuracy <= 0 || accuracy > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo obtener una ubicación precisa. Activa la ubicación y vuelve a intentarlo.");
        }
        long now = System.currentTimeMillis();
        boolean valid = tokenMatches(suppliedToken, settings.currentToken()) && settings.currentExpires() > now
                || tokenMatches(suppliedToken, settings.previousToken()) && settings.previousExpires() > now;
        if (!valid) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El QR venció. Escanea el QR actual que está mostrando administración.");
        double distance = distanceFromLocal(latitude, longitude, settings);
        if (distance > settings.radiusMeters() + Math.min(accuracy, 25))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Estás fuera del radio permitido del local. Acércate al local y vuelve a marcar.");
        return distance;
    }
    private static double distanceFromLocal(double latitude, double longitude, QrSettings settings) {
        double baseLatitude = settings.latitude();
        double baseLongitude = settings.longitude();
        double earthRadius = 6_371_000;
        double lat1 = Math.toRadians(baseLatitude), lat2 = Math.toRadians(latitude);
        double deltaLat = Math.toRadians(latitude - baseLatitude), deltaLon = Math.toRadians(longitude - baseLongitude);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    private static boolean tokenMatches(String supplied, String expected) {
        if (supplied == null || expected == null || supplied.isBlank() || expected.isBlank()) return false;
        return MessageDigest.isEqual(supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8), expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (NumberFormatException ex) { return 0; }
    }
    private static Object safeNumber(Object value) { return value instanceof Number number ? number.doubleValue() : null; }
    private static String normalizeRut(String value) { return value == null ? "" : value.toUpperCase().replaceAll("[.\\-\\s]", ""); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String safeObject(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String workedHours(String entry, String exit, java.sql.Timestamp entryAt, java.sql.Timestamp exitAt) {
        if (exit.isBlank()) return "Pendiente";
        try {
            LocalDateTime start = entryAt == null
                    ? LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.parse(entry, HOUR))
                    : entryAt.toLocalDateTime();
            LocalDateTime end = exitAt == null
                    ? LocalDateTime.of(start.toLocalDate(), LocalTime.parse(exit, HOUR))
                    : exitAt.toLocalDateTime();
            if (end.isBefore(start)) end = end.plusDays(1);
            long minutes = Duration.between(start, end).toMinutes();
            return String.format(java.util.Locale.ROOT, "%d h %02d min", minutes / 60, minutes % 60);
        } catch (java.time.DateTimeException ex) {
            return "Pendiente";
        }
    }
    private record Photo(byte[] bytes, String type, long employeeId) {}
    public record AttendanceLocation(double latitude, double longitude, int radiusMeters) {}
    private record QrSettings(double latitude, double longitude, int radiusMeters, String currentToken,
                              long currentExpires, String previousToken, long previousExpires) {}
}
