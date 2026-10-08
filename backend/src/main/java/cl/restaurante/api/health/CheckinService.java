package cl.restaurante.api.health;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckinService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final NotificationService notifications;

    public CheckinService(
            JdbcTemplate jdbc,
            RoleAccessService access,
            NotificationService notifications) {
        this.jdbc = jdbc;
        this.access = access;
        this.notifications = notifications;
    }

    public static LocalDate operationalDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    @Transactional
    public List<RosterPerson> roster(String rawArea, RoleAccessService.User user) {
        String area = AreaService.valid(rawArea);
        requireAreaAccess(user, area);
        LocalDate today = operationalDate();
        ensureDay(today);
        materializeTasks(today);
        sendDailyNotices(today);

        return jdbc.query(
                "SELECT E.NOMBRE, E.ID_EMPLEADO "
                        + "FROM APP_EMPLEADO E JOIN APP_USUARIO U "
                        + "ON U.ID_EMPLEADO=E.ID_EMPLEADO "
                        + "AND U.ACTIVO='S' "
                        + "WHERE UPPER(E.ESTADO)='ACTIVO' "
                        + "AND TRANSLATE(UPPER(TRIM(E.AREA)),'ÁÉÍÓÚÜ','AEIOUU')=? "
                        + "ORDER BY E.NOMBRE",
                (rs, row) -> {
                    long employee = rs.getLong("ID_EMPLEADO");
                    boolean self = employee == user.employeeId();
                    return new RosterPerson(
                            rs.getString("NOMBRE"),
                            self,
                            self);
                },
                area);
    }

    @Transactional
    public DayView myToday(RoleAccessService.User user) {
        LocalDate today = operationalDate();
        ensureDay(today);
        materializeTasks(today);
        sendDailyNotices(today);
        return dayView(user.employeeId(), today);
    }

    @Transactional
    public DayView myDay(RoleAccessService.User user, LocalDate date) {
        if (date.isAfter(operationalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes consultar un Check-in de una fecha futura.");
        }
        if (date.equals(operationalDate())) {
            return myToday(user);
        }
        return dayView(user.employeeId(), date);
    }

    @Transactional
    public DayView markFree(RoleAccessService.User user) {
        DayView current = myToday(user);
        if (current.completedTasks() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No puedes marcar LIBRE después de completar tareas.");
        }
        jdbc.update(
                "UPDATE APP_CHECKIN_DIA SET ESTADO_DIA='LIBRE' "
                        + "WHERE FECHA_OPERATIVA=? "
                        + "AND ID_EMPLEADO_CLAVE=?",
                Date.valueOf(operationalDate()),
                user.employeeId());
        return dayView(user.employeeId(), operationalDate());
    }

    @Transactional
    public DayView markWorking(RoleAccessService.User user) {
        LocalDate today = operationalDate();
        ensureDay(today);
        jdbc.update(
                "UPDATE APP_CHECKIN_DIA SET ESTADO_DIA='TRABAJADO' "
                        + "WHERE FECHA_OPERATIVA=? "
                        + "AND ID_EMPLEADO_CLAVE=?",
                Date.valueOf(today),
                user.employeeId());
        materializeTasks(today);
        return dayView(user.employeeId(), today);
    }

    @Transactional
    public DayView completeTask(RoleAccessService.User user, long taskId) {
        DayView today = myToday(user);
        if ("LIBRE".equals(today.dayStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu día está marcado LIBRE. Cambia el estado para completar tareas.");
        }
        int changed = jdbc.update(
                "UPDATE APP_CHECKIN_TAREA T SET ESTADO='REALIZADA', "
                        + "REALIZADA_EN=SYSTIMESTAMP "
                        + "WHERE T.ID_CHECKIN_TAREA=? "
                        + "AND T.METODO_COMPLETADO='SIMPLE' "
                        + "AND T.ESTADO='PENDIENTE' AND EXISTS ("
                        + "SELECT 1 FROM APP_CHECKIN_DIA D "
                        + "WHERE D.ID_CHECKIN_DIA=T.ID_CHECKIN_DIA "
                        + "AND D.ID_EMPLEADO_CLAVE=? "
                        + "AND D.FECHA_OPERATIVA=? AND D.ESTADO_DIA='TRABAJADO')",
                taskId,
                user.employeeId(),
                Date.valueOf(operationalDate()));
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La tarea no está disponible para tu usuario o ya fue completada.");
        }
        return dayView(user.employeeId(), operationalDate());
    }

    @Transactional
    public DayView submitPhoto(
            RoleAccessService.User user,
            long taskId,
            byte[] bytes,
            String claimedType,
            String filename) {
        DayView today = myToday(user);
        if ("LIBRE".equals(today.dayStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu día está marcado LIBRE.");
        }
        String type = ImageUpload.validate(bytes, claimedType);
        List<Long> eligible = jdbc.query(
                "SELECT T.ID_CHECKIN_TAREA FROM APP_CHECKIN_TAREA T "
                        + "JOIN APP_CHECKIN_DIA D ON D.ID_CHECKIN_DIA=T.ID_CHECKIN_DIA "
                        + "WHERE T.ID_CHECKIN_TAREA=? "
                        + "AND T.METODO_COMPLETADO='FOTO' AND T.ESTADO='PENDIENTE' "
                        + "AND D.ID_EMPLEADO_CLAVE=? AND D.FECHA_OPERATIVA=? "
                        + "AND D.ESTADO_DIA='TRABAJADO' FOR UPDATE",
                (rs, row) -> rs.getLong(1),
                taskId,
                user.employeeId(),
                Date.valueOf(operationalDate()));
        if (eligible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La tarea no permite foto, no te pertenece o ya fue completada.");
        }
        jdbc.update(
                "INSERT INTO APP_CHECKIN_FOTO "
                        + "(ID_CHECKIN_TAREA,NOMBRE_ARCHIVO,TIPO_CONTENIDO,FOTO) "
                        + "VALUES(?,?,?,?)",
                taskId,
                ImageUpload.safeName(filename),
                type,
                bytes);
        jdbc.update(
                "UPDATE APP_CHECKIN_TAREA SET ESTADO='REALIZADA', "
                        + "REALIZADA_EN=SYSTIMESTAMP "
                        + "WHERE ID_CHECKIN_TAREA=? AND ESTADO='PENDIENTE'",
                taskId);
        return dayView(user.employeeId(), operationalDate());
    }

    public Photo photo(RoleAccessService.User user, long photoId) {
        List<Photo> photos = jdbc.query(
                "SELECT F.FOTO,F.TIPO_CONTENIDO,F.NOMBRE_ARCHIVO, "
                        + "D.ID_EMPLEADO_CLAVE "
                        + "FROM APP_CHECKIN_FOTO F "
                        + "JOIN APP_CHECKIN_TAREA T ON T.ID_CHECKIN_TAREA=F.ID_CHECKIN_TAREA "
                        + "JOIN APP_CHECKIN_DIA D ON D.ID_CHECKIN_DIA=T.ID_CHECKIN_DIA "
                        + "WHERE F.ID_FOTO=?",
                (rs, row) -> new Photo(
                        rs.getBytes("FOTO"),
                        rs.getString("TIPO_CONTENIDO"),
                        rs.getString("NOMBRE_ARCHIVO"),
                        rs.getLong("ID_EMPLEADO_CLAVE")),
                photoId);
        if (photos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "La fotografía no existe o ya fue eliminada por retención semanal.");
        }
        Photo photo = photos.getFirst();
        if (!isAdministrator(user) && photo.employeeKey() != user.employeeId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes abrir tus propias evidencias.");
        }
        if (photo.bytes() == null) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "La fotografía fue eliminada después del cierre semanal.");
        }
        return photo;
    }

    public WeeklyPerson myWeekly(RoleAccessService.User user, LocalDate weekStart) {
        LocalDate start = requireMonday(weekStart);
        List<WeeklyPerson> rows = weeklyRows(
                start,
                start.plusDays(7),
                user.employeeId());
        return combinePerson(user.employeeId(), user.name(), rows);
    }

    public List<WeeklyPoint> myTrend(RoleAccessService.User user) {
        LocalDate currentWeek = operationalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WeeklyPoint> trend = new ArrayList<>();
        for (int offset = 7; offset >= 0; offset--) {
            LocalDate week = currentWeek.minusWeeks(offset);
            WeeklyPerson person = combinePerson(user.employeeId(), user.name(),
                    weeklyRows(week, week.plusDays(7), user.employeeId()));
            trend.add(new WeeklyPoint(week.toString(), person.compliancePercent(),
                    person.assignedTasks(), person.completedTasks()));
        }
        return trend;
    }

    public AdminWeekly adminWeekly(
            RoleAccessService.User user,
            LocalDate weekStart) {
        requireAdministrator(user);
        LocalDate start = requireMonday(weekStart);
        List<WeeklyPerson> people = weeklyRows(
                start,
                start.plusDays(7),
                null);

        Map<String, AreaTotals> areas = new LinkedHashMap<>();
        int daysWorked = 0;
        int daysFree = 0;
        int assigned = 0;
        int completed = 0;
        for (WeeklyPerson person : people) {
            daysWorked += person.daysWorked();
            daysFree += person.daysFree();
            assigned += person.assignedTasks();
            completed += person.completedTasks();
            AreaTotals old = areas.get(person.area());
            areas.put(person.area(), old == null
                    ? new AreaTotals(person.area(), person.daysWorked(),
                            person.daysFree(), person.assignedTasks(),
                            person.completedTasks())
                    : new AreaTotals(person.area(),
                            old.daysWorked() + person.daysWorked(),
                            old.daysFree() + person.daysFree(),
                            old.assignedTasks() + person.assignedTasks(),
                            old.completedTasks() + person.completedTasks()));
        }
        int missing = assigned - completed;
        Integer percentage = assigned == 0
                ? null : Math.round(completed * 100f / assigned);
        return new AdminWeekly(
                start.toString(),
                start.plusDays(6).toString(),
                daysWorked,
                daysFree,
                assigned,
                completed,
                missing,
                percentage,
                new ArrayList<>(areas.values()),
                people);
    }

    @Transactional
    public List<AdminDayPerson> adminDay(
            RoleAccessService.User user,
            String rawArea,
            LocalDate date) {
        requireAdministrator(user);
        String area = AreaService.valid(rawArea);
        if (date.isAfter(operationalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes consultar un Check-in de una fecha futura.");
        }
        if (date.equals(operationalDate())) {
            ensureDay(date);
            materializeTasks(date);
            sendDailyNotices(date);
        }
        return jdbc.query(
                "SELECT D.ID_EMPLEADO_CLAVE,D.NOMBRE_EMPLEADO,D.AREA,D.ESTADO_DIA,"
                        + "COUNT(CASE WHEN D.ESTADO_DIA='TRABAJADO' "
                        + "THEN T.ID_CHECKIN_TAREA END) AS ASIGNADAS,"
                        + "SUM(CASE WHEN D.ESTADO_DIA='TRABAJADO' "
                        + "AND T.ESTADO='REALIZADA' THEN 1 ELSE 0 END) AS HECHAS "
                        + "FROM APP_CHECKIN_DIA D LEFT JOIN APP_CHECKIN_TAREA T "
                        + "ON T.ID_CHECKIN_DIA=D.ID_CHECKIN_DIA "
                        + "WHERE D.FECHA_OPERATIVA=? AND D.AREA=? "
                        + "GROUP BY D.ID_EMPLEADO_CLAVE,D.NOMBRE_EMPLEADO,D.AREA,D.ESTADO_DIA "
                        + "ORDER BY D.NOMBRE_EMPLEADO",
                (rs, row) -> {
                    int assigned = rs.getInt("ASIGNADAS");
                    int done = rs.getInt("HECHAS");
                    Integer percent = assigned == 0 ? null
                            : Math.round(done * 100f / assigned);
                    return new AdminDayPerson(
                            rs.getLong("ID_EMPLEADO_CLAVE"),
                            rs.getString("NOMBRE_EMPLEADO"),
                            rs.getString("AREA"),
                            rs.getString("ESTADO_DIA"),
                            assigned,
                            done,
                            assigned - done,
                            percent);
                },
                Date.valueOf(date),
                area);
    }

    public List<TrendEmployee> adminTrend(RoleAccessService.User user) {
        requireAdministrator(user);
        LocalDate currentWeek = operationalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<Long, List<WeeklyPoint>> byEmployee = new LinkedHashMap<>();
        Map<Long, String[]> labels = new LinkedHashMap<>();
        for (int offset = 7; offset >= 0; offset--) {
            LocalDate week = currentWeek.minusWeeks(offset);
            for (WeeklyPerson person : weeklyRows(week, week.plusDays(7), null)) {
                byEmployee.computeIfAbsent(person.employeeKey(), ignored -> new ArrayList<>())
                        .add(new WeeklyPoint(
                                week.toString(),
                                person.compliancePercent(),
                                person.assignedTasks(),
                                person.completedTasks()));
                labels.put(person.employeeKey(), new String[]{person.name(), person.area()});
            }
        }
        return byEmployee.entrySet().stream()
                .map(entry -> new TrendEmployee(
                        entry.getKey(),
                        labels.get(entry.getKey())[0],
                        labels.get(entry.getKey())[1],
                        entry.getValue()))
                .toList();
    }

    @Transactional
    public void reconcile() {
        LocalDate today = operationalDate();
        ensureDay(today);
        materializeTasks(today);
        sendDailyNotices(today);
        sendWeeklyReports(today);
    }

    private void sendWeeklyReports(LocalDate today) {
        LocalDate lastClosedWeek = today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
        Date firstDate = jdbc.queryForObject(
                "SELECT MIN(FECHA_OPERATIVA) FROM APP_CHECKIN_DIA",
                Date.class);
        if (firstDate == null) return;
        LocalDate week = firstDate.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!week.isAfter(lastClosedWeek)) {
            for (LocalDate day = week; day.isBefore(week.plusDays(7)); day = day.plusDays(1)) {
                ensureDay(day);
                materializeTasks(day);
            }
            deliverWeek(week);
            purgeWeekPhotosIfReportsSaved(week);
            week = week.plusWeeks(1);
        }
    }

    private void deliverWeek(LocalDate week) {
        List<Employee> employees = jdbc.query(
                "SELECT DISTINCT D.ID_EMPLEADO_CLAVE, MAX(D.NOMBRE_EMPLEADO), "
                        + "MAX(D.ID_EMPLEADO) "
                        + "FROM APP_CHECKIN_DIA D WHERE D.FECHA_OPERATIVA>=? "
                        + "AND D.FECHA_OPERATIVA<? "
                        + "GROUP BY D.ID_EMPLEADO_CLAVE",
                (rs, row) -> new Employee(
                        rs.getLong(1), rs.getString(2), rs.getLong(3)),
                Date.valueOf(week),
                Date.valueOf(week.plusDays(7)));

        for (Employee employee : employees) {
            if (employee.id() <= 0) continue;
            List<Long> accountIds = jdbc.query(
                    "SELECT ID_USUARIO FROM APP_USUARIO WHERE ID_EMPLEADO=? "
                            + "AND ACTIVO='S'",
                    (rs, row) -> rs.getLong(1),
                    employee.id());
            if (accountIds.isEmpty()) continue;
            WeeklyPerson report = combinePerson(
                    employee.key(),
                    employee.name(),
                    weeklyRows(week, week.plusDays(7), employee.key()));
            String detail = personalReportText(week, report);
            for (long account : accountIds) {
                deliverOnce(account, "U:" + account,
                        employee.name(), week, "PERSONAL",
                        "Tu resumen semanal de Check-in está disponible.", detail);
            }
        }

        List<AdminRecipient> administrators = jdbc.query(
                "SELECT U.ID_USUARIO,E.NOMBRE FROM APP_USUARIO U "
                        + "JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=U.ID_EMPLEADO "
                        + "WHERE U.ACTIVO='S' AND U.ROL IN ('ADMIN','EMPRESA') "
                        + "ORDER BY U.ID_USUARIO",
                (rs, row) -> new AdminRecipient(rs.getLong(1), rs.getString(2)));
        String globalDetail = adminReportText(
                week,
                weeklyRows(week, week.plusDays(7), null));
        for (AdminRecipient admin : administrators) {
            deliverOnce(admin.userId(), "U:" + admin.userId(),
                    admin.name(), week, "ADMIN",
                    "Reporte semanal Check-in: resumen general disponible.",
                    globalDetail);
        }
    }

    private void deliverOnce(
            long userId,
            String recipientKey,
            String recipientName,
            LocalDate week,
            String type,
            String preview,
            String detail) {
        try {
            jdbc.update(
                    "INSERT INTO APP_CHECKIN_ENVIO_SEMANAL "
                            + "(ID_USUARIO,DESTINATARIO_CLAVE,DESTINATARIO_NOMBRE,"
                            + "SEMANA_INICIO,TIPO_REPORTE,DETALLE,ESTADO_ENVIO) "
                            + "VALUES(?,?,?,?,?,?,'PENDIENTE')",
                    userId, recipientKey, recipientName,
                    Date.valueOf(week), type, detail);
        } catch (DuplicateKeyException alreadyInserted) {
            Integer pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM APP_CHECKIN_ENVIO_SEMANAL "
                            + "WHERE SEMANA_INICIO=? "
                            + "AND TIPO_REPORTE=? AND DESTINATARIO_CLAVE=? "
                            + "AND ESTADO_ENVIO='PENDIENTE'",
                    Integer.class, Date.valueOf(week), type, recipientKey);
            if (pending == null || pending == 0) return;
        }
        notifications.userWithDetail(
                userId, "Check-in Diario", preview, detail);
        jdbc.update(
                "UPDATE APP_CHECKIN_ENVIO_SEMANAL SET ESTADO_ENVIO='ENVIADO', "
                        + "ENVIADO_EN=SYSTIMESTAMP "
                        + "WHERE SEMANA_INICIO=? "
                        + "AND TIPO_REPORTE=? AND DESTINATARIO_CLAVE=? "
                        + "AND ESTADO_ENVIO='PENDIENTE'",
                Date.valueOf(week), type, recipientKey);
    }

    private void purgeWeekPhotosIfReportsSaved(LocalDate week) {
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM APP_CHECKIN_ENVIO_SEMANAL "
                        + "WHERE SEMANA_INICIO=? "
                        + "AND ESTADO_ENVIO<>'ENVIADO'",
                Integer.class, Date.valueOf(week));
        if (pending != null && pending == 0) {
            jdbc.update(
                    "UPDATE APP_CHECKIN_FOTO F SET FOTO=NULL,ELIMINADA_EN=SYSTIMESTAMP "
                            + "WHERE EXISTS ("
                            + "SELECT 1 FROM APP_CHECKIN_TAREA T "
                            + "JOIN APP_CHECKIN_DIA D ON D.ID_CHECKIN_DIA=T.ID_CHECKIN_DIA "
                            + "WHERE T.ID_CHECKIN_TAREA=F.ID_CHECKIN_TAREA "
                            + "AND D.FECHA_OPERATIVA>=? AND D.FECHA_OPERATIVA<?)",
                    Date.valueOf(week), Date.valueOf(week.plusDays(7)));
        }
    }

    private void sendDailyNotices(LocalDate day) {
        List<Employee> employees = jdbc.query(
                "SELECT D.ID_EMPLEADO_CLAVE,D.NOMBRE_EMPLEADO,D.ID_EMPLEADO "
                        + "FROM APP_CHECKIN_DIA D "
                        + "WHERE D.FECHA_OPERATIVA=? AND D.ESTADO_DIA='TRABAJADO' "
                        + "AND D.ID_EMPLEADO IS NOT NULL AND EXISTS ("
                        + "SELECT 1 FROM APP_CHECKIN_TAREA T "
                        + "WHERE T.ID_CHECKIN_DIA=D.ID_CHECKIN_DIA)",
                (rs, row) -> new Employee(
                        rs.getLong(1), rs.getString(2), rs.getLong(3)),
                Date.valueOf(day));
        for (Employee employee : employees) {
            try {
                jdbc.update(
                        "INSERT INTO APP_CHECKIN_AVISO_DIA "
                                + "(FECHA_OPERATIVA,ID_EMPLEADO_CLAVE) "
                                + "VALUES(?,?)",
                        Date.valueOf(day), employee.key());
                notifications.employee(
                        employee.id(),
                        "Check-in Diario",
                        "Tu Check-in Diario está disponible.");
            } catch (DuplicateKeyException alreadyNotified) {
                // El aviso de la fecha ya se registró.
            }
        }
    }

    private void ensureDay(LocalDate date) {
        try {
            jdbc.update(
                    "INSERT INTO APP_CHECKIN_DIA "
                            + "(FECHA_OPERATIVA,ID_EMPLEADO,"
                            + "ID_EMPLEADO_CLAVE,NOMBRE_EMPLEADO,AREA,ESTADO_DIA) "
                            + "SELECT ?,E.ID_EMPLEADO,E.ID_EMPLEADO,"
                            + "E.NOMBRE,TRANSLATE(UPPER(TRIM(E.AREA)),'ÁÉÍÓÚÜ','AEIOUU'),"
                            + "'TRABAJADO' FROM APP_EMPLEADO E "
                            + "WHERE UPPER(E.ESTADO)='ACTIVO' "
                            + "AND TRANSLATE(UPPER(TRIM(E.AREA)),'ÁÉÍÓÚÜ','AEIOUU') "
                            + "IN ('SALON','COPERIA','COCINA','BARRA') "
                            + "AND EXISTS (SELECT 1 FROM APP_USUARIO U "
                            + "WHERE U.ID_EMPLEADO=E.ID_EMPLEADO AND U.ACTIVO='S') "
                            + "AND NOT EXISTS (SELECT 1 FROM APP_CHECKIN_DIA D "
                            + "WHERE D.FECHA_OPERATIVA=? "
                            + "AND D.ID_EMPLEADO_CLAVE=E.ID_EMPLEADO)",
                    Date.valueOf(date), Date.valueOf(date));
        } catch (DuplicateKeyException concurrentRequest) {
            // Las restricciones únicas hacen segura la generación concurrente.
        }
    }

    private void materializeTasks(LocalDate date) {
        try {
            jdbc.update(
                    "INSERT /*+ DISABLE_PARALLEL_DML */ INTO APP_CHECKIN_TAREA "
                            + "(ID_CHECKIN_DIA,ID_PLANTILLA,"
                            + "TAREA_SNAPSHOT,METODO_COMPLETADO,ESTADO) "
                            + "SELECT D.ID_CHECKIN_DIA,P.ID_PLANTILLA,"
                            + "P.TAREA,P.METODO_COMPLETADO,'PENDIENTE' "
                            + "FROM APP_CHECKIN_DIA D JOIN APP_CHECKIN_PLANTILLA P "
                            + "ON P.AREA=D.AREA "
                            + "WHERE D.FECHA_OPERATIVA=? "
                            + "AND D.ESTADO_DIA='TRABAJADO' AND P.ACTIVA='S' "
                            + "AND P.VIGENTE_DESDE<=? "
                            + "AND (P.VIGENTE_HASTA IS NULL OR P.VIGENTE_HASTA>=?) "
                            + "AND NOT EXISTS (SELECT 1 FROM APP_CHECKIN_TAREA T "
                            + "WHERE T.ID_CHECKIN_DIA=D.ID_CHECKIN_DIA "
                            + "AND T.ID_PLANTILLA=P.ID_PLANTILLA)",
                    Date.valueOf(date), Date.valueOf(date), Date.valueOf(date));
        } catch (DuplicateKeyException concurrentRequest) {
            // La clave única impide duplicar tareas si coinciden dos solicitudes.
        }
    }

    private DayView dayView(long employeeId, LocalDate date) {
        List<DayHead> heads = jdbc.query(
                "SELECT ID_CHECKIN_DIA,FECHA_OPERATIVA,"
                        + "ID_EMPLEADO_CLAVE,NOMBRE_EMPLEADO,AREA,ESTADO_DIA "
                        + "FROM APP_CHECKIN_DIA WHERE ID_EMPLEADO_CLAVE=? "
                        + "AND FECHA_OPERATIVA=?",
                (rs, row) -> new DayHead(
                        rs.getLong("ID_CHECKIN_DIA"),
                        rs.getDate("FECHA_OPERATIVA").toLocalDate(),
                        rs.getLong("ID_EMPLEADO_CLAVE"),
                        rs.getString("NOMBRE_EMPLEADO"),
                        rs.getString("AREA"),
                        rs.getString("ESTADO_DIA")),
                employeeId, Date.valueOf(date));
        if (heads.isEmpty()) {
            return new DayView(date.toString(), "", "", "", List.of(), 0, 0, 0);
        }
        DayHead head = heads.getFirst();
        List<CheckinTask> tasks = jdbc.query(
                "SELECT T.ID_CHECKIN_TAREA,T.TAREA_SNAPSHOT,"
                        + "T.METODO_COMPLETADO,T.ESTADO,T.REALIZADA_EN,"
                        + "(SELECT MAX(F.ID_FOTO) FROM APP_CHECKIN_FOTO F "
                        + "WHERE F.ID_CHECKIN_TAREA=T.ID_CHECKIN_TAREA) AS ID_FOTO "
                        + "FROM APP_CHECKIN_TAREA T WHERE T.ID_CHECKIN_DIA=? "
                        + "ORDER BY T.ID_CHECKIN_TAREA",
                (rs, row) -> new CheckinTask(
                        rs.getLong("ID_CHECKIN_TAREA"),
                        rs.getString("TAREA_SNAPSHOT"),
                        rs.getString("METODO_COMPLETADO"),
                        rs.getString("ESTADO"),
                        rs.getTimestamp("REALIZADA_EN") == null ? null
                                : rs.getTimestamp("REALIZADA_EN").toLocalDateTime().toString(),
                        nullableLong(rs.getObject("ID_FOTO"))),
                head.id());
        int completed = (int) tasks.stream()
                .filter(task -> "REALIZADA".equals(task.status())).count();
        int percentage = tasks.isEmpty() ? 0 : Math.round(completed * 100f / tasks.size());
        return new DayView(
                head.date().toString(), head.area(), head.status(), head.name(),
                tasks, tasks.size(), completed, percentage);
    }

    private List<WeeklyPerson> weeklyRows(
            LocalDate start,
            LocalDate end,
            Long employeeKey) {
        String sql = "SELECT D.ID_EMPLEADO_CLAVE,D.NOMBRE_EMPLEADO,D.AREA,"
                + "COUNT(DISTINCT CASE WHEN D.ESTADO_DIA='TRABAJADO' "
                + "THEN D.FECHA_OPERATIVA END) AS DIAS_TRABAJADOS,"
                + "COUNT(DISTINCT CASE WHEN D.ESTADO_DIA='LIBRE' "
                + "THEN D.FECHA_OPERATIVA END) AS DIAS_LIBRES,"
                + "COUNT(CASE WHEN D.ESTADO_DIA='TRABAJADO' "
                + "THEN T.ID_CHECKIN_TAREA END) AS ASIGNADAS,"
                + "SUM(CASE WHEN D.ESTADO_DIA='TRABAJADO' "
                + "AND T.ESTADO='REALIZADA' THEN 1 ELSE 0 END) AS HECHAS "
                + "FROM APP_CHECKIN_DIA D LEFT JOIN APP_CHECKIN_TAREA T "
                + "ON T.ID_CHECKIN_DIA=D.ID_CHECKIN_DIA "
                + "WHERE D.FECHA_OPERATIVA>=? "
                + "AND D.FECHA_OPERATIVA<? "
                + (employeeKey == null ? "" : "AND D.ID_EMPLEADO_CLAVE=? ")
                + "GROUP BY D.ID_EMPLEADO_CLAVE,D.NOMBRE_EMPLEADO,D.AREA "
                + "ORDER BY D.AREA,D.NOMBRE_EMPLEADO";
        Object[] args = employeeKey == null
                ? new Object[]{Date.valueOf(start), Date.valueOf(end)}
                : new Object[]{Date.valueOf(start), Date.valueOf(end), employeeKey};
        return jdbc.query(sql, (rs, row) -> {
            int assigned = rs.getInt("ASIGNADAS");
            int completed = rs.getInt("HECHAS");
            Integer percentage = assigned == 0 ? null
                    : Math.round(completed * 100f / assigned);
            return new WeeklyPerson(
                    rs.getLong("ID_EMPLEADO_CLAVE"),
                    rs.getString("NOMBRE_EMPLEADO"),
                    rs.getString("AREA"),
                    rs.getInt("DIAS_TRABAJADOS"),
                    rs.getInt("DIAS_LIBRES"),
                    assigned,
                    completed,
                    assigned - completed,
                    percentage);
        }, args);
    }

    private WeeklyPerson combinePerson(
            long key,
            String name,
            List<WeeklyPerson> rows) {
        if (rows.isEmpty()) {
            return new WeeklyPerson(key, name, "", 0, 0, 0, 0, 0, null);
        }
        int worked = 0, free = 0, assigned = 0, completed = 0;
        String area = rows.getFirst().area();
        for (WeeklyPerson row : rows) {
            worked += row.daysWorked();
            free += row.daysFree();
            assigned += row.assignedTasks();
            completed += row.completedTasks();
        }
        return new WeeklyPerson(key, name, area, worked, free, assigned,
                completed, assigned - completed,
                assigned == 0 ? null : Math.round(completed * 100f / assigned));
    }

    private String personalReportText(LocalDate week, WeeklyPerson report) {
        return "Tu Check-in Diario — semana del " + week + " al " + week.plusDays(6)
                + "\nDías trabajados: " + report.daysWorked()
                + "\nDías libres: " + report.daysFree()
                + "\nTareas asignadas: " + report.assignedTasks()
                + "\nTareas realizadas: " + report.completedTasks()
                + "\nTareas no realizadas: " + report.incompleteTasks()
                + "\nCumplimiento: " + percentLabel(report.compliancePercent());
    }

    private String adminReportText(LocalDate week, List<WeeklyPerson> rows) {
        StringBuilder text = new StringBuilder("REPORTE ADMINISTRATIVO CHECK-IN\n")
                .append("Semana: ").append(week).append(" al ").append(week.plusDays(6)).append('\n');
        int assigned = 0, completed = 0, free = 0, worked = 0;
        for (WeeklyPerson row : rows) {
            assigned += row.assignedTasks();
            completed += row.completedTasks();
            free += row.daysFree();
            worked += row.daysWorked();
        }
        text.append("Cumplimiento general: ")
                .append(assigned == 0 ? "Sin tareas asignadas"
                        : Math.round(completed * 100f / assigned) + "%")
                .append("\nDías trabajados: ").append(worked)
                .append("\nDías libres: ").append(free)
                .append("\nTareas asignadas: ").append(assigned)
                .append("\nTareas realizadas: ").append(completed)
                .append("\nTareas no realizadas: ").append(assigned - completed)
                .append("\n\nDETALLE POR TRABAJADOR\n");
        for (WeeklyPerson row : rows) {
            text.append("\n").append(row.name()).append(" — ").append(row.area())
                    .append(" — ").append(percentLabel(row.compliancePercent()))
                    .append(" — Hechas ").append(row.completedTasks())
                    .append('/').append(row.assignedTasks())
                    .append(" — Libres ").append(row.daysFree());
        }
        return text.toString();
    }

    private void requireAreaAccess(RoleAccessService.User user, String area) {
        if (isAdministrator(user)) return;
        String ownArea = AreaService.normalize(
                new AreaService(jdbc, access).employeeArea(user.employeeId()));
        if (!ownArea.equals(area)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes consultar el área asignada a tu usuario.");
        }
    }

    private void requireAdministrator(RoleAccessService.User user) {
        if (!isAdministrator(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo administración puede consultar el reporte global de Check-in.");
        }
    }

    private boolean isAdministrator(RoleAccessService.User user) {
        return "ADMIN".equals(user.role()) || "EMPRESA".equals(user.role());
    }

    private LocalDate requireMonday(LocalDate date) {
        if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La semana debe comenzar un lunes.");
        }
        return date;
    }

    private static String percentLabel(Integer value) {
        return value == null ? "Sin tareas asignadas" : value + "%";
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private record DayHead(long id, LocalDate date,
            long employeeKey, String name, String area, String status) {}
    private record Employee(long key, String name, long id) {}
    private record AdminRecipient(long userId, String name) {}

    public record RosterPerson(String name, boolean self, boolean canOpen) {}
    public record CheckinTask(long id, String title, String method, String status,
            String completedAt, Long photoId) {}
    public record DayView(String date, String area, String dayStatus, String employeeName,
            List<CheckinTask> tasks, int assignedTasks, int completedTasks,
            int compliancePercent) {}
    public record WeeklyPerson(long employeeKey, String name, String area,
            int daysWorked, int daysFree, int assignedTasks, int completedTasks,
            int incompleteTasks, Integer compliancePercent) {}
    public record AreaTotals(String area, int daysWorked, int daysFree,
            int assignedTasks, int completedTasks) {}
    public record AdminWeekly(String weekStart, String weekEnd,
            int daysWorked, int daysFree, int assignedTasks, int completedTasks,
            int incompleteTasks, Integer compliancePercent,
            List<AreaTotals> areas, List<WeeklyPerson> employees) {}
    public record AdminDayPerson(long employeeKey, String name, String area,
            String dayStatus, int assignedTasks, int completedTasks,
            int incompleteTasks, Integer compliancePercent) {}
    public record WeeklyPoint(String weekStart, Integer compliancePercent,
            int assignedTasks, int completedTasks) {}
    public record TrendEmployee(long employeeKey, String name, String area,
            List<WeeklyPoint> weeks) {}
    public record Photo(byte[] bytes, String type, String filename, long employeeKey) {}
}
