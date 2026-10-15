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

    public byte[] template() {
        requireSchema();
        List<Employee> employees = activeEmployees();
        if (employees.stream().anyMatch(employee -> employee.rut() == null || employee.rut().isBlank()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay personal activo de Cocina, Salón o Barra sin RUT registrado. Completa el RUT antes de descargar la plantilla.");
        Set<String> templateRuts = new HashSet<>();
        for (Employee employee : employees) if (!templateRuts.add(normalizeRut(employee.rut())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay RUT repetidos entre el personal activo. Corrige los datos antes de descargar la plantilla.");
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 15); titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(titleFont);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont); headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND); headerStyle.setWrapText(true);
            CellStyle totalStyle = workbook.createCellStyle(); totalStyle.setDataFormat(workbook.createDataFormat().getFormat("0.##"));
            for (String area : AREAS) for (int week = 1; week <= 2; week++) {
                Sheet sheet = workbook.createSheet(sheetName(area, week));
                Row title = sheet.createRow(0); Cell titleCell = title.createCell(0); titleCell.setCellValue("Plantilla de asistencia · " + areaLabel(area) + " · Semana " + week); titleCell.setCellStyle(titleStyle);
                Row instructions = sheet.createRow(1); instructions.createCell(0).setCellValue("Completa cada turno como HH:mm-HH:mm o escribe LIBRE. Indica horas del día. No cambies los encabezados ni el orden de las columnas.");
                String[] headers = headers(); Row header = sheet.createRow(3);
                for (int col = 0; col < headers.length; col++) { Cell cell = header.createCell(col); cell.setCellValue(headers[col]); cell.setCellStyle(headerStyle); }
                int rowIndex = 4;
                for (Employee employee : employees) if (area.equals(employee.area())) {
                    Row row = sheet.createRow(rowIndex);
                    row.createCell(0).setCellValue(employee.name()); row.createCell(1).setCellValue(employee.rut());
                    Cell total = row.createCell(16); total.setCellFormula("SUM(D" + (rowIndex + 1) + ",F" + (rowIndex + 1) + ",H" + (rowIndex + 1) + ",J" + (rowIndex + 1) + ",L" + (rowIndex + 1) + ",N" + (rowIndex + 1) + ",P" + (rowIndex + 1) + ")"); total.setCellStyle(totalStyle);
                    rowIndex++;
                }
                if (rowIndex == 4) rowIndex++;
                for (int col = 0; col < headers.length; col++) sheet.autoSizeColumn(col);
                sheet.setColumnWidth(0, Math.max(sheet.getColumnWidth(0), 6200)); sheet.setColumnWidth(1, Math.max(sheet.getColumnWidth(1), 3800));
                for (int col = 2; col <= 14; col += 2) sheet.setColumnWidth(col, 4400);
                for (int col = 3; col <= 15; col += 2) sheet.setColumnWidth(col, 3300);
                sheet.setColumnWidth(16, 3600); sheet.createFreezePane(2, 4); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(3, rowIndex - 1, 0, 16));
            }
            workbook.write(output); return output.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo crear la plantilla de horarios.");
        }
    }

    @Transactional
    public Map<String, Object> importWorkbook(MultipartFile file, LocalDate startsOn, long importedBy) throws IOException {
        requireSchema();
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw bad("Selecciona una planilla XLSX de hasta 10 MB.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw bad("La planilla debe ser un archivo XLSX descargado desde Asistencia.");
        if (startsOn == null || startsOn.getDayOfWeek() != DayOfWeek.MONDAY) throw bad("La vigencia debe comenzar un lunes para mantener el ciclo de semanas.");
        LocalDate endsOn = startsOn.plusMonths(2).minusDays(1);
        List<Employee> active = activeEmployees();
        Map<String, Employee> byRut = new HashMap<>();
        for (Employee employee : active) {
            String key = normalizeRut(employee.rut());
            if (key.isBlank()) throw bad("Hay personal activo de Cocina, Salón o Barra sin RUT registrado. Completa el RUT antes de cargar la planilla.");
            if (byRut.put(key, employee) != null) throw bad("Hay RUT repetidos entre el personal activo. Corrige los datos antes de importar.");
        }
        List<ShiftRow> imported = new ArrayList<>();
        Map<String, String> homeAreaByRut = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (String area : AREAS) for (int week = 1; week <= 2; week++) {
                Sheet sheet = workbook.getSheet(sheetName(area, week));
                if (sheet == null) throw bad("Falta la hoja '" + sheetName(area, week) + "'. Descarga la plantilla estándar y no cambies los nombres de sus hojas.");
                validateHeaders(sheet.getRow(3), formatter, evaluator);
                Set<String> areaSeen = new HashSet<>();
                for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r); if (row == null) continue;
                    String name = text(row.getCell(0), formatter, evaluator), rut = text(row.getCell(1), formatter, evaluator);
                    if (name.isBlank() && rut.isBlank()) continue;
                    if (name.isBlank() || rut.isBlank()) throw bad("En '" + sheet.getSheetName() + "', fila " + (r + 1) + ": completa Nombre y RUT.");
                    String normalizedRut = normalizeRut(rut);
                    Employee employee = byRut.get(normalizedRut);
                    if (employee == null) throw bad("El RUT " + rut + " de '" + sheet.getSheetName() + "' no corresponde a una persona activa registrada.");
                    if (!area.equals(employee.area())) throw bad(employee.name() + " pertenece a " + areaLabel(employee.area()) + " y aparece en la hoja de " + areaLabel(area) + ".");
                    if (!normalizeName(employee.name()).equals(normalizeName(name))) throw bad("El nombre asociado al RUT " + rut + " no coincide con el registro de Personal. Descarga una plantilla actualizada.");
                    if (!areaSeen.add(normalizedRut)) throw bad("La persona con RUT " + rut + " aparece más de una vez en '" + sheet.getSheetName() + "'.");
                    String priorArea = homeAreaByRut.putIfAbsent(normalizedRut, area);
                    if (priorArea != null && !priorArea.equals(area)) throw bad("La persona con RUT " + rut + " aparece en más de un horario de área.");
                    for (int day = 0; day < 7; day++) {
                        int shiftCol = 2 + day * 2, hoursCol = shiftCol + 1;
                        String shiftText = text(row.getCell(shiftCol), formatter, evaluator).trim();
                        String hoursText = text(row.getCell(hoursCol), formatter, evaluator).trim().replace(',', '.');
                        if (shiftText.isBlank() || hoursText.isBlank()) throw bad("En '" + sheet.getSheetName() + "', fila " + (r + 1) + ": completa el turno y las horas de " + DAYS.get(day) + ". Usa LIBRE y 0 para un día libre.");
                        double hours;
                        try { hours = Double.parseDouble(hoursText); } catch (NumberFormatException ex) { throw bad("Horas no válidas en " + DAYS.get(day) + " de " + employee.name() + "."); }
                        if (!Double.isFinite(hours) || hours < 0 || hours > 24) throw bad("Las horas deben estar entre 0 y 24.");
                        String state = shiftText.equalsIgnoreCase("LIBRE") ? "LIBRE" : "TRABAJA";
                        LocalTime start = null, end = null;
                        if ("LIBRE".equals(state)) {
                            if (hours != 0) throw bad("Los días LIBRE deben tener 0 horas.");
                        } else {
                            Matcher matcher = SHIFT.matcher(shiftText);
                            if (!matcher.matches() || hours <= 0) throw bad("Turno no válido para " + employee.name() + " el " + DAYS.get(day) + ". Usa HH:mm-HH:mm y horas mayores que 0.");
                            start = LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
                            end = LocalTime.of(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
                            if (start.equals(end)) throw bad("La hora de inicio y término no pueden ser iguales.");
                        }
                        imported.add(new ShiftRow(employee, area, week, day + 1, start, end, state, hours));
                    }
                    double total = number(row.getCell(16), formatter, evaluator);
                    double sum = imported.subList(imported.size() - 7, imported.size()).stream().mapToDouble(ShiftRow::hours).sum();
                    if (Math.abs(total - sum) > 0.01) throw bad("El total semanal de " + employee.name() + " no coincide con la suma de las horas diarias en '" + sheet.getSheetName() + "'.");
                }
                Set<String> expected = new HashSet<>();
                for (Employee employee : active) if (area.equals(employee.area())) expected.add(normalizeRut(employee.rut()));
                if (!expected.equals(areaSeen)) throw bad("La lista de personal de '" + sheet.getSheetName() + "' no coincide con el personal activo del área. Descarga nuevamente la plantilla.");
            }
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw bad("No se pudo leer la planilla. Usa el archivo XLSX estándar descargado desde Asistencia."); }

        jdbc.update("DELETE FROM APP_ASISTENCIA_PLANILLA WHERE VIGENTE_DESDE>=?", java.sql.Date.valueOf(startsOn));
        jdbc.update("UPDATE APP_ASISTENCIA_PLANILLA SET VIGENTE_HASTA=? WHERE VIGENTE_DESDE<? AND VIGENTE_HASTA>=?",
                java.sql.Date.valueOf(startsOn.minusDays(1)), java.sql.Date.valueOf(startsOn), java.sql.Date.valueOf(startsOn));
        Map<String, Long> planIds = new HashMap<>();
        for (String area : AREAS) {
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var ps = connection.prepareStatement("INSERT INTO APP_ASISTENCIA_PLANILLA(AREA,VIGENTE_DESDE,VIGENTE_HASTA,CREADO_POR) VALUES(?,?,?,?)", new String[]{"ID_PLANILLA"});
                ps.setString(1, area); ps.setDate(2, java.sql.Date.valueOf(startsOn)); ps.setDate(3, java.sql.Date.valueOf(endsOn)); ps.setLong(4, importedBy); return ps;
            }, key);
            if (key.getKey() == null) throw new IllegalStateException("No se pudo crear la versión de horario.");
            planIds.put(area, key.getKey().longValue());
        }
        for (ShiftRow row : imported) jdbc.update("INSERT INTO APP_ASISTENCIA_HORARIO(ID_PLANILLA,EMPLEADO_ID,RUT_SNAPSHOT,NOMBRE_SNAPSHOT,SEMANA_CICLO,DIA_SEMANA,HORA_INICIO,HORA_FIN,ESTADO_DIA,HORAS_PROGRAMADAS) VALUES(?,?,?,?,?,?,?,?,?,?)",
                planIds.get(row.area()), row.employee().id(), row.employee().rut(), row.employee().name(), row.week(), row.day(), row.start() == null ? null : row.start().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), row.end() == null ? null : row.end().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), row.state(), row.hours());
        return Map.of("vigenteDesde", startsOn.toString(), "vigenteHasta", endsOn.toString(), "personas", byRut.size(), "turnos", imported.size());
    }

    public void addScheduleDetails(List<Map<String, Object>> attendanceRows, LocalDate from, LocalDate to, Long selectedEmployee) {
        if (!schemaReady()) return;
        List<ScheduleRow> definitions = jdbc.query("SELECT P.AREA,P.VIGENTE_DESDE,P.VIGENTE_HASTA,D.EMPLEADO_ID,D.RUT_SNAPSHOT,D.NOMBRE_SNAPSHOT,D.SEMANA_CICLO,D.DIA_SEMANA,D.HORA_INICIO,D.HORA_FIN,D.ESTADO_DIA,D.HORAS_PROGRAMADAS FROM APP_ASISTENCIA_PLANILLA P JOIN APP_ASISTENCIA_HORARIO D ON D.ID_PLANILLA=P.ID_PLANILLA WHERE P.VIGENTE_DESDE<=? AND P.VIGENTE_HASTA>=?" + (selectedEmployee == null ? "" : " AND D.EMPLEADO_ID=?"),
                (rs, n) -> new ScheduleRow(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getDouble(12)),
                selectedEmployee == null ? new Object[]{java.sql.Date.valueOf(to), java.sql.Date.valueOf(from)} : new Object[]{java.sql.Date.valueOf(to), java.sql.Date.valueOf(from), selectedEmployee});
        if (definitions.isEmpty()) return;
        Map<String, ScheduleRow> byCycleDay = new HashMap<>();
        for (ScheduleRow definition : definitions) byCycleDay.put(key(definition.employeeId(), definition.area(), definition.week(), definition.day()), definition);
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
                int week = (int) (ChronoUnit.DAYS.between(plan.from(), date) / 7 % 2) + 1;
                ScheduleRow shift = byCycleDay.get(key(plan.employeeId(), plan.area(), week, day));
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
        List<ScheduleRow> definitions = jdbc.query("SELECT P.AREA,P.VIGENTE_DESDE,P.VIGENTE_HASTA,D.EMPLEADO_ID,D.RUT_SNAPSHOT,D.NOMBRE_SNAPSHOT,D.SEMANA_CICLO,D.DIA_SEMANA,D.HORA_INICIO,D.HORA_FIN,D.ESTADO_DIA,D.HORAS_PROGRAMADAS FROM APP_ASISTENCIA_PLANILLA P JOIN APP_ASISTENCIA_HORARIO D ON D.ID_PLANILLA=P.ID_PLANILLA WHERE P.VIGENTE_DESDE<=? AND P.VIGENTE_HASTA>=?",
                (rs, n) -> new ScheduleRow(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getDouble(12)), java.sql.Date.valueOf(date), java.sql.Date.valueOf(date));
        int weekDay = date.getDayOfWeek().getValue();
        for (ScheduleRow plan : definitions) {
            int cycle = (int) (ChronoUnit.DAYS.between(plan.from(), date) / 7 % 2) + 1;
            if (cycle != plan.week() || weekDay != plan.day()) continue;
            Map<String, Object> row = new LinkedHashMap<>(); row.put("empleadoId", plan.employeeId()); row.put("nombre", plan.name()); row.put("rut", plan.rut()); row.put("area", areaLabel(plan.area()));
            row.put("turnoProgramado", "TRABAJA".equals(plan.state()) ? plan.start() + "–" + plan.end() : "LIBRE"); row.put("estadoHorario", plan.state()); row.put("horasProgramadas", plan.hours()); rows.add(row);
        }
        return rows;
    }

    public boolean schemaReady() {
        return Boolean.TRUE.equals(jdbc.execute(connection -> {
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "APP_ASISTENCIA_PLANILLA", new String[]{"TABLE"})) { return tables.next(); }
        }));
    }

    private void requireSchema() { if (!schemaReady()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero ejecuta la migración 11_planillas_asistencia.sql en Oracle."); }
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
    private static String normalizeRut(String value) { return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[.\\-\\s]", ""); }
    private static String text(Cell cell, DataFormatter formatter, org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) { return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim(); }
    private static double number(Cell cell, DataFormatter formatter, org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        if (cell == null) throw bad("Falta el total semanal de una persona.");
        try { return Double.parseDouble(text(cell, formatter, evaluator).replace(',', '.')); }
        catch (NumberFormatException ex) { throw bad("El total semanal debe ser un número calculado por la plantilla."); }
    }
    private static int minutes(String value) { try { LocalTime time = LocalTime.parse(value); return time.getHour() * 60 + time.getMinute(); } catch (Exception ex) { return 0; } }
    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String key(long employeeId, String area, int week, int day) { return employeeId + "|" + area + "|" + week + "|" + day; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private record Employee(long id, String name, String rut, String area) {}
    private record ShiftRow(Employee employee, String area, int week, int day, LocalTime start, LocalTime end, String state, double hours) {}
    private record ScheduleRow(String area, LocalDate from, LocalDate to, long employeeId, String rut, String name, int week, int day,
                               String start, String end, String state, double hours) {}
}
