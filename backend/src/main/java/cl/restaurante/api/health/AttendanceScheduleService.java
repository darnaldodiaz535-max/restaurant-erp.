package cl.restaurante.api.health;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceScheduleService {
    private static final List<String> AREAS = List.of("COCINA", "SALON", "BARRA");
    private static final List<String> DAYS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");
    private static final Pattern SHIFT = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)\\s*[-–]\\s*([01]?\\d|2[0-3]):([0-5]\\d)$");
    private final JdbcTemplate jdbc;

    public AttendanceScheduleService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static List<Map<String, String>> templateOptions() {
        return List.of(
                Map.of("id", "SEMANA_FIJA", "label", "Horario semanal fijo", "file", "01_horario_semanal_fijo.xlsx"),
                Map.of("id", "ROTACION_2_SEMANAS", "label", "Rotación de dos semanas", "file", "02_horario_rotativo_dos_semanas.xlsx"),
                Map.of("id", "CICLO_DIAS", "label", "Ciclo rotativo (día/noche, 4x4 u otro)", "file", "03_horario_ciclo_rotativo.xlsx"),
                Map.of("id", "CALENDARIO_FECHAS", "label", "Horario definido por fechas", "file", "04_horario_por_fechas.xlsx"));
    }

    public String templateFileName(String format) { return templateOption(format).get("file"); }

    public byte[] template(String format, LocalDate requestedStart) {
        requireSchema();
        LocalDate startsOn = requestedStart == null ? LocalDate.now(java.time.ZoneId.of("America/Santiago")).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)) : requestedStart;
        LocalDate endsOn = startsOn.plusMonths(2).minusDays(1);
        int cycleDays = cycleDays(format, startsOn, endsOn);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int sheetCount = (cycleDays + 6) / 7;
            for (int week = 1; week <= sheetCount; week++) {
                Sheet sheet = workbook.createSheet("Semana " + week);
                createWeekSheet(workbook, sheet, format, startsOn, week);
            }
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar la plantilla seleccionada.");
        }
    }

    private int cycleDays(String format, LocalDate startsOn, LocalDate endsOn) {
        return switch (format) {
            case "SEMANA_FIJA" -> 7;
            case "ROTACION_2_SEMANAS" -> 14;
            case "CICLO_DIAS" -> 28;
            case "CALENDARIO_FECHAS" -> (int) ChronoUnit.DAYS.between(startsOn, endsOn) + 1;
            default -> throw bad("Formato de horario no válido.");
        };
    }

    private void createWeekSheet(Workbook workbook, Sheet sheet, String format, LocalDate startsOn, int week) {
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 16); titleStyle.setFont(titleFont);
        CellStyle headerStyle = workbook.createCellStyle(); headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex()); headerStyle.setFont(headerFont);
        CellStyle inputStyle = workbook.createCellStyle(); inputStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        CellStyle hoursStyle = workbook.createCellStyle(); hoursStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        CellStyle noteStyle = workbook.createCellStyle(); noteStyle.setWrapText(true);

        Row title = sheet.createRow(0); title.createCell(0).setCellValue("PLANILLA DE HORARIO · " + sheet.getSheetName().toUpperCase(Locale.ROOT));
        title.getCell(0).setCellStyle(titleStyle); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 15));
        Row instructions = sheet.createRow(1); instructions.createCell(0).setCellValue("Escribe el nombre registrado de cada trabajador. En cada día indica HH:mm-HH:mm (ej. 10:00-22:00) o LIBRE. Las horas y el total se calculan automáticamente.");
        instructions.getCell(0).setCellStyle(noteStyle); sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 15)); instructions.setHeightInPoints(30);
        Row info = sheet.createRow(2); info.createCell(0).setCellValue("Formato"); info.createCell(1).setCellValue(format);
        info.createCell(3).setCellValue("Vigente desde"); info.createCell(4).setCellValue(startsOn.toString());
        info.createCell(6).setCellValue("Semana del ciclo"); info.createCell(7).setCellValue(week);
        if (format.equals("CALENDARIO_FECHAS")) {
            LocalDate endsOn = startsOn.plusMonths(2).minusDays(1);
            CellStyle dateStyle = workbook.createCellStyle(); dateStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd-mmm-yyyy"));
            Row dates = sheet.createRow(3);
            for (int day = 0; day < 7; day++) {
                LocalDate date = startsOn.plusDays((week - 1L) * 7 + day);
                if (!date.isAfter(endsOn)) {
                    int col = 1 + day * 2;
                    Cell dateCell = dates.createCell(col); dateCell.setCellValue(java.sql.Date.valueOf(date)); dateCell.setCellStyle(dateStyle);
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(3, 3, col, col + 1));
                }
            }
        }
        Row headers = sheet.createRow(4); headers.createCell(0).setCellValue("Nombre"); headers.getCell(0).setCellStyle(headerStyle);
        for (int day = 0; day < 7; day++) {
            int dayCol = 1 + day * 2;
            headers.createCell(dayCol).setCellValue(DAYS.get(day)); headers.getCell(dayCol).setCellStyle(headerStyle);
            headers.createCell(dayCol + 1).setCellValue("Horas"); headers.getCell(dayCol + 1).setCellStyle(headerStyle);
        }
        headers.createCell(15).setCellValue("Total general"); headers.getCell(15).setCellStyle(headerStyle);
        // Espacio para ingresar hasta 100 personas sin modificar la estructura.
        for (int rowNum = 5; rowNum < 105; rowNum++) {
            Row row = sheet.createRow(rowNum);
            for (int col = 0; col <= 15; col++) {
                Cell cell = row.createCell(col);
                if (col == 0 || col == 15 || (col % 2 == 1)) cell.setCellStyle(inputStyle);
                else cell.setCellStyle(hoursStyle);
            }
            for (int day = 0; day < 7; day++) {
                int dayCol = 1 + day * 2, hoursCol = dayCol + 1;
                String dayCell = new org.apache.poi.ss.util.CellReference(rowNum, dayCol).formatAsString();
                row.getCell(hoursCol).setCellFormula("IF(" + dayCell + "=\"\",\"\",IF(UPPER(" + dayCell + ")=\"LIBRE\",0,IFERROR(MOD(TIMEVALUE(RIGHT(" + dayCell + ",5))-TIMEVALUE(LEFT(" + dayCell + ",5)),1)*24,0)))");
            }
            row.getCell(15).setCellFormula("SUM(C" + (rowNum + 1) + ",E" + (rowNum + 1) + ",G" + (rowNum + 1) + ",I" + (rowNum + 1) + ",K" + (rowNum + 1) + ",M" + (rowNum + 1) + ",O" + (rowNum + 1) + ")");
        }
        sheet.setColumnWidth(0, 30 * 256);
        for (int col = 1; col <= 14; col++) sheet.setColumnWidth(col, col % 2 == 1 ? 18 * 256 : 10 * 256);
        sheet.setColumnWidth(15, 16 * 256); sheet.createFreezePane(1, 5);
    }

    private static Map<String, String> templateOption(String format) {
        return templateOptions().stream().filter(option -> option.get("id").equalsIgnoreCase(format)).findFirst()
                .orElseThrow(() -> bad("Selecciona uno de los cuatro formatos de horario disponibles."));
    }

    @Transactional
    public Map<String, Object> importWorkbook(MultipartFile file, String format, LocalDate startsOn, long importedBy) throws IOException {
        requireSchema();
        templateOption(format);
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw bad("Selecciona una planilla XLSX de hasta 10 MB.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw bad("La planilla debe ser un archivo XLSX descargado desde Asistencia.");
        if (startsOn == null) throw bad("Selecciona la fecha de inicio del horario.");
        if (startsOn.getDayOfWeek() != DayOfWeek.MONDAY) throw bad("El horario debe comenzar un lunes para que las columnas coincidan con los días de la semana.");
        LocalDate endsOn = startsOn.plusMonths(2).minusDays(1);
        List<Employee> active = activeEmployees();
        Map<String, List<Employee>> byName = new HashMap<>();
        for (Employee employee : active) {
            byName.computeIfAbsent(normalizeName(employee.name()), ignored -> new ArrayList<>()).add(employee);
        }
        List<ShiftRow> imported = new ArrayList<>();
        int cycleDays = cycleDays(format, startsOn, endsOn);
        Set<Long> importedEmployees = new HashSet<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            if (cycleDays < 1 || cycleDays > 62) throw bad("El ciclo debe tener entre 1 y 62 días.");
            int expectedSheets = (cycleDays + 6) / 7;
            if (workbook.getNumberOfSheets() != expectedSheets) throw bad("El archivo no corresponde al formato seleccionado. Descarga nuevamente esa plantilla.");
            Set<String> seen = new HashSet<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                Row info = sheet.getRow(2);
                if (info == null || !format.equals(text(info.getCell(1), formatter, evaluator))) throw bad("El archivo no corresponde al formato seleccionado. Descarga nuevamente esa plantilla.");
                int week = (int) number(info.getCell(7), formatter, evaluator);
                if (week != sheetIndex + 1) throw bad("Las pestañas de semana deben conservar su orden original.");
                Row header = sheet.getRow(4);
                if (header == null || !normalizeName(text(header.getCell(0), formatter, evaluator)).equals("NOMBRE")
                        || !normalizeName(text(header.getCell(15), formatter, evaluator)).equals("TOTAL GENERAL"))
                    throw bad("No cambies los encabezados ni su orden. Descarga nuevamente la plantilla.");
                for (int day = 0; day < 7; day++) {
                    int col = 1 + day * 2;
                    if (!normalizeName(text(header.getCell(col), formatter, evaluator)).equals(normalizeName(DAYS.get(day)))
                            || !normalizeName(text(header.getCell(col + 1), formatter, evaluator)).equals("HORAS"))
                        throw bad("No cambies los encabezados ni su orden. Descarga nuevamente la plantilla.");
                }
                for (int r = 5; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r); if (row == null) continue;
                    String name = text(row.getCell(0), formatter, evaluator);
                    boolean hasShift = false;
                    for (int day = 0; day < 7; day++) if (!text(row.getCell(1 + day * 2), formatter, evaluator).isBlank()) hasShift = true;
                    if (name.isBlank() && !hasShift) continue;
                    if (name.isBlank()) throw bad("Pestaña " + sheet.getSheetName() + ", fila " + (r + 1) + ": escribe el nombre antes de completar sus turnos.");
                    List<Employee> matches = byName.getOrDefault(normalizeName(name), List.of());
                    if (matches.isEmpty()) throw bad("El nombre '" + name + "' no coincide con un trabajador activo registrado en Personal.");
                    if (matches.size() > 1) throw bad("El nombre '" + name + "' está repetido en Personal; debe ser único para reconocerlo sin RUT.");
                    Employee employee = matches.get(0);
                    importedEmployees.add(employee.id());
                    for (int day = 0; day < 7; day++) {
                        int dayCol = 1 + day * 2;
                        String shift = text(row.getCell(dayCol), formatter, evaluator).trim();
                        boolean inRange = format.equals("CALENDARIO_FECHAS")
                                ? (week - 1) * 7 + day < cycleDays : true;
                        if (!inRange) {
                            if (!shift.isBlank()) throw bad("La fecha de la pestaña " + sheet.getSheetName() + " queda fuera de los dos meses seleccionados.");
                            continue;
                        }
                        if (shift.isBlank()) throw bad("Pestaña " + sheet.getSheetName() + ", " + DAYS.get(day) + ": escribe LIBRE o un turno HH:mm-HH:mm.");
                        int index = format.equals("SEMANA_FIJA") ? day + 1 : (week - 1) * 7 + day + 1;
                        if (index < 1 || index > cycleDays) continue;
                        if (!seen.add(employee.id() + "|" + index)) throw bad("Hay un día repetido para " + employee.name() + " en " + sheet.getSheetName() + ".");
                        boolean off = normalizeName(shift).equals("LIBRE") || normalizeName(shift).equals("DESCANSO");
                        LocalTime startTime = null, endTime = null;
                        double hours = 0;
                        if (!off) {
                            Matcher matcher = SHIFT.matcher(shift);
                            if (!matcher.matches()) throw bad("Pestaña " + sheet.getSheetName() + ", " + DAYS.get(day) + ": usa HH:mm-HH:mm o LIBRE.");
                            startTime = parseClock(matcher.group(1) + ":" + matcher.group(2));
                            endTime = parseClock(matcher.group(3) + ":" + matcher.group(4));
                            if (startTime.equals(endTime)) throw bad("El turno de " + employee.name() + " no puede durar 0 o 24 horas; revisa entrada y salida.");
                            long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
                            if (minutes < 0) minutes += 24 * 60;
                            hours = minutes / 60.0;
                            if (hours <= 0 || hours > 24) throw bad("Revisa la entrada y salida de " + employee.name() + ".");
                        }
                        imported.add(new ShiftRow(employee, employee.area(), index, 1, startTime, endTime, off ? "LIBRE" : "TRABAJA", hours));
                    }
                }
            }
            if(imported.isEmpty()) throw bad("La planilla no tiene turnos. Complétala antes de cargarla.");
            for (Employee employee : active) if (importedEmployees.contains(employee.id()))
                for (int i = 1; i <= cycleDays; i++) if (!seen.contains(employee.id() + "|" + i))
                    throw bad("Falta un día, incluidos los LIBRE, para " + employee.name() + " en el ciclo.");
            if (imported.stream().anyMatch(row -> row.employee().rut() == null || row.employee().rut().isBlank()) && !rutSnapshotAllowsNull())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Para cargar personal sin RUT, ejecuta también la migración 13_rut_opcional_horarios.sql en Oracle.");
        } catch(ResponseStatusException ex) { throw ex; }
        catch(Exception ex) { throw bad("No se pudo leer la plantilla. Comprueba el formato de horas HH:mm y conserva los encabezados originales."); }

        jdbc.update("DELETE FROM APP_ASISTENCIA_PLANILLA WHERE VIGENTE_DESDE>=?", java.sql.Date.valueOf(startsOn));
        jdbc.update("UPDATE APP_ASISTENCIA_PLANILLA SET VIGENTE_HASTA=? WHERE VIGENTE_DESDE<? AND VIGENTE_HASTA>=?", java.sql.Date.valueOf(startsOn.minusDays(1)), java.sql.Date.valueOf(startsOn), java.sql.Date.valueOf(startsOn));
        Map<String,Long> planIds=new HashMap<>();
        for(String area:AREAS) {
            KeyHolder key=new GeneratedKeyHolder(); final int finalCycleDays=cycleDays;
            jdbc.update(connection->{ var ps=connection.prepareStatement("INSERT INTO APP_ASISTENCIA_PLANILLA(AREA,VIGENTE_DESDE,VIGENTE_HASTA,CREADO_POR,TIPO_CICLO,CICLO_DIAS) VALUES(?,?,?,?,?,?)",new String[]{"ID_PLANILLA"});
                ps.setString(1,area); ps.setDate(2,java.sql.Date.valueOf(startsOn)); ps.setDate(3,java.sql.Date.valueOf(endsOn)); ps.setLong(4,importedBy); ps.setString(5,format); ps.setInt(6,finalCycleDays); return ps; },key);
            if(key.getKey()==null) throw new IllegalStateException("No se pudo crear la versión de horario."); planIds.put(area,key.getKey().longValue());
        }
        for(ShiftRow row:imported) jdbc.update("INSERT INTO APP_ASISTENCIA_HORARIO(ID_PLANILLA,EMPLEADO_ID,RUT_SNAPSHOT,NOMBRE_SNAPSHOT,SEMANA_CICLO,DIA_SEMANA,HORA_INICIO,HORA_FIN,ESTADO_DIA,HORAS_PROGRAMADAS) VALUES(?,?,?,?,?,?,?,?,?,?)",
                planIds.get(row.area()),row.employee().id(),row.employee().rut(),row.employee().name(),row.week(),row.day(),row.start()==null?null:row.start().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),row.end()==null?null:row.end().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),row.state(),row.hours());
        return Map.of("vigenteDesde",startsOn.toString(),"vigenteHasta",endsOn.toString(),"personas",importedEmployees.size(),"turnos",imported.size());
    }

    private static LocalTime parseClock(String value) {
        try { return LocalTime.parse(value.trim().length()==4?"0"+value.trim():value.trim()); }
        catch(Exception ex) { throw bad("Las horas de entrada y salida deben tener formato HH:mm."); }
    }
    public void addScheduleDetails(List<Map<String, Object>> attendanceRows, LocalDate from, LocalDate to, Long selectedEmployee) {
        if (!schemaReady()) return;
        List<ScheduleRow> definitions = jdbc.query("SELECT P.AREA,P.VIGENTE_DESDE,P.VIGENTE_HASTA,D.EMPLEADO_ID,D.RUT_SNAPSHOT,D.NOMBRE_SNAPSHOT,D.SEMANA_CICLO,D.DIA_SEMANA,D.HORA_INICIO,D.HORA_FIN,D.ESTADO_DIA,D.HORAS_PROGRAMADAS,P.TIPO_CICLO,P.CICLO_DIAS FROM APP_ASISTENCIA_PLANILLA P JOIN APP_ASISTENCIA_HORARIO D ON D.ID_PLANILLA=P.ID_PLANILLA WHERE P.VIGENTE_DESDE<=? AND P.VIGENTE_HASTA>=?" + (selectedEmployee == null ? "" : " AND D.EMPLEADO_ID=?"),
                (rs, n) -> new ScheduleRow(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getDouble(12), rs.getString(13), rs.getInt(14)),
                selectedEmployee == null ? new Object[]{java.sql.Date.valueOf(to), java.sql.Date.valueOf(from)} : new Object[]{java.sql.Date.valueOf(to), java.sql.Date.valueOf(from), selectedEmployee});
        if (definitions.isEmpty()) return;
        Map<String, ScheduleRow> byCycleDay = new HashMap<>();
        for (ScheduleRow definition : definitions) byCycleDay.put(key(definition.employeeId(), definition.area(), storedCycleIndex(definition), 1), definition);
        Map<String, ScheduleRow> plans = new LinkedHashMap<>();
        for (ScheduleRow definition : definitions) {
            plans.putIfAbsent(definition.employeeId() + "|" + definition.area() + "|" + definition.from() + "|" + definition.to(), definition);
        }
        Map<String, Map<String, Object>> actualByPersonDay = new HashMap<>();
        for (Map<String, Object> row : attendanceRows) {
            String k = row.get("empleadoId") + "|" + row.get("fecha");
            actualByPersonDay.put(k, row);
        }
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Santiago"));
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int day = date.getDayOfWeek().getValue();
            for (ScheduleRow plan : plans.values()) {
                if (date.isBefore(plan.from()) || date.isAfter(plan.to())) continue;
                int offset = (int) ChronoUnit.DAYS.between(plan.from(), date);
                int cycleIndex = plan.type().equals("CALENDARIO_FECHAS") ? offset + 1 : offset % plan.cycleDays() + 1;
                ScheduleRow shift = byCycleDay.get(key(plan.employeeId(), plan.area(), cycleIndex, 1));
                if (shift == null) continue;
                Map<String, Object> row = actualByPersonDay.get(plan.employeeId() + "|" + date);
                if (row == null) {
                    row = new LinkedHashMap<>(); row.put("id", 0L); row.put("empleadoId", plan.employeeId());
                    row.put("nombre", plan.name()); row.put("rut", plan.rut()); row.put("fecha", date.toString());
                    row.put("entrada", ""); row.put("salida", ""); row.put("horasTrabajadas", "—");
                    row.put("entradaFoto", false); row.put("salidaFoto", false);
                    String status = "LIBRE".equals(shift.state()) ? "LIBRE" : date.isAfter(today) ? "PROGRAMADO" : "SIN MARCA";
                    row.put("estadoHorario", status);
                    attendanceRows.add(row); actualByPersonDay.put(plan.employeeId() + "|" + date, row);
                }
                row.put("area", areaLabel(plan.area())); row.put("turnoProgramado", "TRABAJA".equals(shift.state()) ? shift.start() + "–" + shift.end() : "LIBRE");
                row.put("entradaProgramada", shift.start() == null ? "" : shift.start()); row.put("salidaProgramada", shift.end() == null ? "" : shift.end());
                row.put("horasProgramadas", shift.hours());
                String actualEntry = safe(row.get("entrada")), actualExit = safe(row.get("salida"));
                if ("LIBRE".equals(shift.state())) row.put("estadoHorario", actualEntry.isBlank() && actualExit.isBlank() ? "LIBRE" : "MARCA EN DÍA LIBRE");
                else if (actualEntry.isBlank()) row.put("estadoHorario", date.isAfter(today) ? "PROGRAMADO" : "SIN MARCA");
                else {
                    int delta = minutes(actualEntry) - minutes(shift.start());
                    row.put("minutosEntrada", delta);
                    row.put("estadoHorario", delta > 0 ? "TARDE · " + delta + " min" : delta < 0 ? "TEMPRANO · " + (-delta) + " min" : "PUNTUAL");
                    if (!actualExit.isBlank()) {
                        int exitDelta = minutes(actualExit) - minutes(shift.end());
                        row.put("minutosSalida", exitDelta);
                        String exitStatus = exitDelta < 0 ? "Salida anticipada " + (-exitDelta) + " min" : exitDelta > 0 ? "Salida " + exitDelta + " min después" : "Salida a la hora";
                        row.put("estadoHorario", row.get("estadoHorario") + " · " + exitStatus);
                    }
                }
            }
        }
        attendanceRows.sort((a, b) -> {
            int byName = safe(a.get("nombre")).compareToIgnoreCase(safe(b.get("nombre")));
            return byName != 0 ? byName : safe(a.get("fecha")).compareTo(safe(b.get("fecha")));
        });
    }

    public List<Map<String, Object>> scheduleForDate(LocalDate date) {
        if (!schemaReady()) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ScheduleRow> definitions = jdbc.query("SELECT P.AREA,P.VIGENTE_DESDE,P.VIGENTE_HASTA,D.EMPLEADO_ID,D.RUT_SNAPSHOT,D.NOMBRE_SNAPSHOT,D.SEMANA_CICLO,D.DIA_SEMANA,D.HORA_INICIO,D.HORA_FIN,D.ESTADO_DIA,D.HORAS_PROGRAMADAS,P.TIPO_CICLO,P.CICLO_DIAS FROM APP_ASISTENCIA_PLANILLA P JOIN APP_ASISTENCIA_HORARIO D ON D.ID_PLANILLA=P.ID_PLANILLA WHERE P.VIGENTE_DESDE<=? AND P.VIGENTE_HASTA>=?",
                (rs, n) -> new ScheduleRow(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getDouble(12), rs.getString(13), rs.getInt(14)), java.sql.Date.valueOf(date), java.sql.Date.valueOf(date));
        for (ScheduleRow plan : definitions) {
            int offset=(int)ChronoUnit.DAYS.between(plan.from(),date);
            int cycleIndex=plan.type().equals("CALENDARIO_FECHAS")?offset+1:offset%plan.cycleDays()+1;
            if (cycleIndex != storedCycleIndex(plan)) continue;
            Map<String, Object> row = new LinkedHashMap<>(); row.put("empleadoId", plan.employeeId()); row.put("nombre", plan.name()); row.put("rut", plan.rut()); row.put("area", areaLabel(plan.area()));
            row.put("turnoProgramado", "TRABAJA".equals(plan.state()) ? plan.start() + "–" + plan.end() : "LIBRE"); row.put("estadoHorario", plan.state()); row.put("horasProgramadas", plan.hours()); rows.add(row);
        }
        return rows;
    }

    public boolean schemaReady() {
        return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            boolean tableExists;
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "APP_ASISTENCIA_PLANILLA", new String[]{"TABLE"})) { tableExists = tables.next(); }
            if (!tableExists) return false;
            Set<String> columns = new HashSet<>();
            try (var metadata = connection.getMetaData().getColumns(connection.getCatalog(), null, "APP_ASISTENCIA_PLANILLA", null)) {
                while (metadata.next()) columns.add(metadata.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
            return columns.contains("TIPO_CICLO") && columns.contains("CICLO_DIAS");
        }));
    }

    private void requireSchema() {
        if (Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "APP_ASISTENCIA_PLANILLA", new String[]{"TABLE"})) { return tables.next(); }
        })) && !schemaReady()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ejecuta la migración 12_horarios_ciclos_flexibles.sql en Oracle para activar los cuatro formatos de horario.");
        if (!schemaReady()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero ejecuta la migración 11_planillas_asistencia.sql y luego 12_horarios_ciclos_flexibles.sql en Oracle.");
    }
    private boolean rutSnapshotAllowsNull() {
        return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "APP_ASISTENCIA_HORARIO", "RUT_SNAPSHOT")) {
                return columns.next() && columns.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable;
            }
        }));
    }
    private List<Employee> activeEmployees() {
        return jdbc.query("SELECT ID_EMPLEADO,NOMBRE,RUT,AREA FROM APP_EMPLEADO WHERE ESTADO='ACTIVO' ORDER BY NOMBRE", (rs, n) -> {
            String area = normalizeArea(rs.getString(4)); return area == null ? null : new Employee(rs.getLong(1), rs.getString(2), rs.getString(3), area);
        }).stream().filter(java.util.Objects::nonNull).toList();
    }
    private static String[] headers() {
        List<String> headers = new ArrayList<>(List.of("Nombre", "RUT"));
        for (String day : DAYS) { headers.add(day); headers.add("Horas " + day.toLowerCase(Locale.ROOT)); }
        headers.add("Total de horas"); return headers.toArray(String[]::new);
    }
    private static void validateHeaders(Row row, DataFormatter formatter, org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        if (row == null) throw bad("La hoja no contiene los encabezados de la plantilla.");
        String[] expected = headers();
        for (int i = 0; i < expected.length; i++) if (!normalizeName(expected[i]).equals(normalizeName(text(row.getCell(i), formatter, evaluator))))
            throw bad("Los encabezados o el orden de columnas fueron cambiados. Descarga la plantilla estándar.");
    }
    private static String sheetName(String area, int week) { return area.equals("SALON") ? "Salon Semana " + week : areaLabel(area) + " Semana " + week; }
    private static String areaLabel(String area) { return switch (area) { case "SALON" -> "Salón"; case "COCINA" -> "Cocina"; case "BARRA" -> "Barra"; default -> area; }; }
    private static String normalizeArea(String value) {
        String area = normalizeName(value);
        if (area.contains("COCINA")) return "COCINA";
        if (area.contains("SALON") || area.contains("SALA")) return "SALON";
        if (area.contains("BARRA")) return "BARRA";
        return null;
    }
    private static String normalizeName(String value) { return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").toUpperCase(Locale.ROOT); }
    private static String text(Cell cell, DataFormatter formatter, org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) { return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim(); }
    private static double number(Cell cell, DataFormatter formatter, org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        if (cell == null) throw bad("Falta el total semanal de una persona.");
        try { return Double.parseDouble(text(cell, formatter, evaluator).replace(',', '.')); }
        catch (NumberFormatException ex) { throw bad("El total semanal debe ser un número calculado por la plantilla."); }
    }
    private static int minutes(String value) { try { LocalTime time = LocalTime.parse(value); return time.getHour() * 60 + time.getMinute(); } catch (Exception ex) { return 0; } }
    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String key(long employeeId, String area, int week, int day) { return employeeId + "|" + area + "|" + week + "|" + day; }
    private static int storedCycleIndex(ScheduleRow row) { return row.week() <= 2 && row.day() > 1 ? (row.week() - 1) * 7 + row.day() : row.week(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private record Employee(long id, String name, String rut, String area) {}
    private record ShiftRow(Employee employee, String area, int week, int day, LocalTime start, LocalTime end, String state, double hours) {}
    private record ScheduleRow(String area, LocalDate from, LocalDate to, long employeeId, String rut, String name, int week, int day,
                               String start, String end, String state, double hours, String type, int cycleDays) {}
}
