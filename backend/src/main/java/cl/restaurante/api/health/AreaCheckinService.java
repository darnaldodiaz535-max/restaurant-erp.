package cl.restaurante.api.health;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Area-wide Check-in. Legacy personal records remain in APP_CHECKIN_DIA. */
@Service
public class AreaCheckinService {
    public static final List<String> AREAS = List.of("COCINA", "SALON", "BARRA", "COPERIA");
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public AreaCheckinService(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    public static String area(String value) {
        String code = AreaService.normalize(value);
        if (!AREAS.contains(code)) throw error(HttpStatus.BAD_REQUEST, "Selecciona Cocina, Salón, Barra o Copería.");
        return code;
    }

    public static boolean admin(RoleAccessService.User user) {
        return List.of("ADMIN", "EMPRESA").contains(user.role());
    }

    public static String managedArea(RoleAccessService.User user) {
        return switch (user.role()) {
            case "JEFE_COCINA" -> "COCINA";
            case "JEFE_SALON" -> "SALON";
            case "JEFE_BARRA" -> "BARRA";
            case "JEFE_COPERIA" -> "COPERIA";
            default -> null;
        };
    }

    private void requireReport(RoleAccessService.User user, String area) {
        if (!admin(user) && !area.equals(managedArea(user)))
            throw error(HttpStatus.FORBIDDEN, "Solo puedes consultar el resumen de tu propia área.");
    }

    @Transactional
    public Day day(RoleAccessService.User user, String rawArea, LocalDate date) {
        String area = area(rawArea);
        if (date == null || date.isAfter(CheckinService.operationalDate()))
            throw error(HttpStatus.BAD_REQUEST, "Selecciona una fecha válida, sin fechas futuras.");
        reconcile();
        List<Task> tasks = tasks(area, date, date.plusDays(1));
        return new Day(date.toString(), area, area.equals(managedArea(user)) && date.equals(CheckinService.operationalDate()), tasks);
    }

    private List<Task> tasks(String area, LocalDate from, LocalDate until) {
        return jdbc.query("SELECT R.*, F.ID_FOTO FROM APP_CHECKIN_REGISTRO R "
                + "LEFT JOIN APP_CHECKIN_EVIDENCIA F ON F.ID_REGISTRO=R.ID_REGISTRO "
                + "WHERE R.AREA=? AND R.FECHA<? AND (R.FECHA>=? OR (R.FRECUENCIA='SEMANAL' AND R.FECHA+7>?)) ORDER BY R.FECHA,R.ID_REGISTRO",
                (rs, n) -> new Task(rs.getLong("ID_REGISTRO"), rs.getDate("FECHA").toLocalDate().toString(),
                        rs.getString("AREA"), rs.getString("TAREA"), rs.getString("METODO"), rs.getString("ESTADO"), rs.getString("FRECUENCIA"),
                        rs.getString("RESPONSABLE"), rs.getTimestamp("REALIZADA_EN") == null ? null : rs.getTimestamp("REALIZADA_EN").toLocalDateTime().toString(),
                        rs.getObject("ID_FOTO") == null ? null : rs.getLong("ID_FOTO")),
                area, Date.valueOf(until), Date.valueOf(from), Date.valueOf(from));
    }

    @Transactional
    public void complete(RoleAccessService.User user, long id, byte[] bytes, String claimedType, String filename) {
        String own = managedArea(user);
        if (own == null) throw error(HttpStatus.FORBIDDEN, "Solo el jefe del área puede confirmar tareas.");
        // Serialize confirmations with the weekly closure, including across midnight.
        jdbc.queryForObject("SELECT INICIO FROM APP_CHECKIN_CONFIG WHERE ID=1 FOR UPDATE", Date.class);
        var rows = jdbc.query("SELECT AREA,FECHA,METODO,ESTADO,FRECUENCIA FROM APP_CHECKIN_REGISTRO WHERE ID_REGISTRO=? FOR UPDATE",
                (rs, n) -> new String[]{rs.getString(1), rs.getDate(2).toLocalDate().toString(), rs.getString(3), rs.getString(4), rs.getString(5)}, id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "La tarea no existe.");
        String[] task = rows.getFirst();
        if (!own.equals(task[0])) throw error(HttpStatus.FORBIDDEN, "No puedes modificar tareas de otra área.");
        LocalDate today = CheckinService.operationalDate();
        LocalDate start = LocalDate.parse(task[1]);
        boolean inPeriod = "SEMANAL".equals(task[4])
                ? !today.isBefore(start) && today.isBefore(start.plusDays(7)) : today.equals(start);
        if (!inPeriod || !"PENDIENTE".equals(task[3]))
            throw error(HttpStatus.CONFLICT, "Solo se pueden completar tareas pendientes de su período actual.");
        if ("FOTO".equals(task[2])) {
            if (bytes == null || bytes.length == 0 || bytes.length > 4 * 1024 * 1024)
                throw error(HttpStatus.BAD_REQUEST, "Adjunta una fotografía válida de hasta 4 MB.");
            String type = ImageUpload.validate(bytes, claimedType);
            jdbc.update("INSERT INTO APP_CHECKIN_EVIDENCIA(ID_REGISTRO,NOMBRE_ARCHIVO,TIPO_CONTENIDO,FOTO) VALUES(?,?,?,?)",
                    id, ImageUpload.safeName(filename), type, bytes);
        } else if (bytes != null) {
            throw error(HttpStatus.BAD_REQUEST, "Esta tarea se confirma sin fotografía.");
        }
        jdbc.update("UPDATE APP_CHECKIN_REGISTRO SET ESTADO='REALIZADA',ID_RESPONSABLE=?,RESPONSABLE=?,REALIZADA_EN=SYSTIMESTAMP WHERE ID_REGISTRO=?",
                user.id(), user.name(), id);
    }

    public Evidence photo(RoleAccessService.User user, long id) {
        // Every authenticated worker may view evidence, including historic evidence.
        var rows = jdbc.query("SELECT FOTO,TIPO_CONTENIDO,NOMBRE_ARCHIVO FROM APP_CHECKIN_EVIDENCIA WHERE ID_FOTO=?",
                (rs, n) -> new Evidence(rs.getBytes(1), rs.getString(2), rs.getString(3)), id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "La fotografía no existe.");
        return rows.getFirst();
    }

    @Transactional
    public List<Report> weekly(RoleAccessService.User user, String rawArea, LocalDate start) {
        if (start == null || start.getDayOfWeek() != DayOfWeek.MONDAY || start.isAfter(CheckinService.operationalDate()))
            throw error(HttpStatus.BAD_REQUEST, "Selecciona el lunes de una semana actual o anterior.");
        List<String> selected;
        if (rawArea == null) {
            if (!admin(user)) throw error(HttpStatus.FORBIDDEN, "Solo administración puede ver el resumen general.");
            selected = AREAS;
        } else {
            String area = area(rawArea);
            requireReport(user, area);
            selected = List.of(area);
        }
        reconcile();
        return selected.stream().map(a -> report(a, start)).toList();
    }

    @Transactional
    public List<String> weeks(RoleAccessService.User user, String rawArea) {
        String area = area(rawArea);
        requireReport(user, area);
        reconcile();
        return jdbc.query("SELECT SEMANA FROM APP_CHECKIN_RESUMEN WHERE AREA=? ORDER BY SEMANA DESC",
                (rs, n) -> rs.getDate(1).toLocalDate().toString(), area);
    }

    private Report report(String area, LocalDate start) {
        var tasks = tasks(area, start, start.plusDays(7));
        int completed = (int) tasks.stream().filter(t -> "REALIZADA".equals(t.status())).count();
        boolean closed = start.plusDays(7).compareTo(CheckinService.operationalDate()) <= 0;
        return new Report(area, start.toString(), start.plusDays(6).toString(), tasks.size(), completed,
                tasks.size() - completed, tasks.isEmpty() ? null : Math.round(completed * 100f / tasks.size()),
                !tasks.isEmpty() && completed == 0, closed, tasks);
    }

    @Transactional
    public List<Suggestion> suggestions(RoleAccessService.User user, String rawArea) {
        String area = area(rawArea);
        requireReport(user, area);
        return jdbc.query("SELECT * FROM APP_CHECKIN_OBSERVACION WHERE AREA=? ORDER BY CREADO_EN DESC,ID_OBSERVACION DESC",
                (rs, n) -> new Suggestion(rs.getLong("ID_OBSERVACION"), area, rs.getString("MENSAJE"),
                        rs.getString("REMITENTE"), rs.getLong("ID_DESTINATARIO"), rs.getString("DESTINATARIO"),
                        rs.getTimestamp("CREADO_EN").toLocalDateTime().toString()), area);
    }

    @Transactional
    public void suggest(RoleAccessService.User user, String rawArea, String message) {
        if (!admin(user)) throw error(HttpStatus.FORBIDDEN, "Solo administración puede enviar observaciones.");
        String area = area(rawArea);
        if (message == null || message.isBlank() || message.trim().length() > 2000)
            throw error(HttpStatus.BAD_REQUEST, "Escribe una observación de 1 a 2000 caracteres.");
        var recipients = recipients("JEFE_" + area);
        if (recipients.isEmpty()) throw error(HttpStatus.CONFLICT, "No hay un jefe activo asignado a esta área.");
        for (Recipient recipient : recipients) {
            jdbc.update("INSERT INTO APP_CHECKIN_OBSERVACION(AREA,MENSAJE,ID_REMITENTE,REMITENTE,ID_DESTINATARIO,DESTINATARIO) VALUES(?,?,?,?,?,?)",
                    area, message.trim(), user.id(), user.name(), recipient.id(), recipient.name());
            notifications.checkinNotice(recipient.id(), "Nuevo mensaje de administración · " + area, message.trim());
        }
    }

    private List<Recipient> recipients(String role) {
        return jdbc.query("SELECT U.ID_USUARIO,E.NOMBRE FROM APP_USUARIO U JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=U.ID_EMPLEADO "
                + "WHERE U.ACTIVO='S' AND U.ROL=?", (rs, n) -> new Recipient(rs.getLong(1), rs.getString(2)), role);
    }

    /** Rebuilds missed operational dates after downtime; never fabricates completion. */
    @Transactional
    public void reconcile() {
        LocalDate today = CheckinService.operationalDate();
        LocalDate activation = jdbc.queryForObject("SELECT INICIO FROM APP_CHECKIN_CONFIG WHERE ID=1 FOR UPDATE", Date.class).toLocalDate();
        Date lastClosed = jdbc.queryForObject("SELECT MAX(SEMANA) FROM APP_CHECKIN_RESUMEN", Date.class);
        if (lastClosed != null && lastClosed.toLocalDate().plusWeeks(1).isAfter(activation))
            activation = lastClosed.toLocalDate().plusWeeks(1);
        // Finalized weeks are immutable. Revisit only unclosed dates after downtime.
        for (LocalDate date = activation; !date.isAfter(today); date = date.plusDays(1)) {
            try {
                jdbc.update("INSERT INTO APP_CHECKIN_REGISTRO(ID_PLANTILLA,FECHA,AREA,TAREA,METODO,FRECUENCIA) "
                        + "SELECT P.ID_PLANTILLA,CASE WHEN P.FRECUENCIA='SEMANAL' THEN ? ELSE ? END,P.AREA,P.TAREA,P.METODO_COMPLETADO,P.FRECUENCIA FROM APP_CHECKIN_PLANTILLA P "
                        + "WHERE P.AREA IN ('COCINA','SALON','BARRA','COPERIA') AND P.ACTIVA='S' "
                        + "AND P.VIGENTE_DESDE<=? AND (P.VIGENTE_HASTA IS NULL OR P.VIGENTE_HASTA>=?) "
                        + "AND NOT EXISTS(SELECT 1 FROM APP_CHECKIN_REGISTRO R WHERE R.ID_PLANTILLA=P.ID_PLANTILLA AND R.FECHA=CASE WHEN P.FRECUENCIA='SEMANAL' THEN ? ELSE ? END) "
                        + "AND NOT EXISTS(SELECT 1 FROM APP_CHECKIN_RESUMEN S WHERE S.AREA=P.AREA AND S.SEMANA<=? AND S.SEMANA+7>?)",
                        Date.valueOf(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))), Date.valueOf(date),
                        Date.valueOf(date), Date.valueOf(date),
                        Date.valueOf(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))), Date.valueOf(date),
                        Date.valueOf(date), Date.valueOf(date));
            } catch (DuplicateKeyException concurrent) { /* unique (template,date) */ }
        }
        LocalDate current = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (LocalDate week = activation.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); week.isBefore(current); week = week.plusWeeks(1)) {
            for (String area : AREAS) saveReport(area, week);
        }
    }

    private void saveReport(String area, LocalDate week) {
        Report report = report(area, week);
        try {
            jdbc.update("INSERT INTO APP_CHECKIN_RESUMEN(AREA,SEMANA,TOTAL,COMPLETADAS,PENDIENTES) VALUES(?,?,?,?,?)",
                    area, Date.valueOf(week), report.total(), report.completed(), report.pending());
        } catch (DuplicateKeyException saved) { return; }
        String detail = area + " · " + week + " al " + week.plusDays(6) + "\nCompletadas: " + report.completed()
                + "/" + report.total() + "\nCumplimiento: " + (report.percent() == null ? "Sin tareas" : report.percent() + "%")
                + "\nPendientes/incumplidas: " + report.pending()
                + (report.zeroCompletion() ? "\nATENCIÓN: ninguna tarea completada." : "");
        for (String role : List.of("ADMIN", "EMPRESA", "JEFE_" + area)) {
            for (Recipient recipient : recipients(role))
                notifications.checkinNotice(recipient.id(), "Resumen semanal de Check-in disponible · " + area, detail);
        }
    }

    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private record Recipient(long id, String name) {}
    public record Task(long id, String date, String area, String title, String method, String status, String frequency, String responsible, String completedAt, Long photoId) {}
    public record Day(String date, String area, boolean canManage, List<Task> tasks) {}
    public record Report(String area, String weekStart, String weekEnd, int total, int completed, int pending,
                         Integer percent, boolean zeroCompletion, boolean closed, List<Task> tasks) {}
    public record Suggestion(long id, String area, String message, String sender, long recipientId, String recipient, String createdAt) {}
    public record Evidence(byte[] bytes, String type, String filename) {}
}
