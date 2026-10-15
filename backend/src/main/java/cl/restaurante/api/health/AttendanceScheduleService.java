package cl.restaurante.api.health;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
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
        String file = templateOption(format).get("file");
        LocalDate startsOn = requestedStart == null ? LocalDate.now(java.time.ZoneId.of("America/Santiago")).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)) : requestedStart;
        LocalDate endsOn = startsOn.plusMonths(2).minusDays(1);
        try (var input = getClass().getResourceAsStream("/attendance-templates/" + file)) {
            if (input == null) throw new IOException("Plantilla no encontrada");
            List<Employee> employees = activeEmployees();
            if (employees.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Registra primero el personal activo para poder preparar la plantilla.");
            Set<String> ruts = new HashSet<>();
            for (Employee employee : employees) {
                if (employee.rut() == null || employee.rut().isBlank()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay personal activo sin RUT. Completa Personal antes de descargar la plantilla.");
                if (!ruts.add(normalizeRut(employee.rut()))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay RUT repetidos entre el personal activo. Corrige Personal antes de descargar la plantilla.");
            }
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(input.readAllBytes())); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.getSheet("Horario");
                int cycle = switch (format) { case "SEMANA_FIJA" -> 7; case "ROTACION_2_SEMANAS" -> 14; case "CICLO_DIAS" -> 28; default -> (int) ChronoUnit.DAYS.between(startsOn, endsOn) + 1; };
                if (format.equals("CICLO_DIAS")) sheet.getRow(2).getCell(1).setCellValue(cycle);
                var dateStyle = workbook.createCellStyle(); dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
                sheet.getRow(1).getCell(5).setCellValue(startsOn.toString()); sheet.getRow(1).getCell(7).setCellValue(endsOn.toString());
                int rowIndex = 7;
                for (Employee employee : employees) for (int i = 0; i < cycle; i++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) row = sheet.createRow(rowIndex);
                    Row styleSource = sheet.getRow(7);
                    for (int c = 0; c < 11; c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) cell = row.createCell(c);
                        if (styleSource != null && styleSource.getCell(c) != null) cell.setCellStyle(styleSource.getCell(c).getCellStyle());
                    }
                    row.getCell(0).setCellValue(areaLabel(employee.area()).toUpperCase(Locale.ROOT));
                    row.getCell(1).setCellValue(employee.name()); row.getCell(2).setCellValue(employee.rut());
                    if (format.equals("SEMANA_FIJA")) row.getCell(3).setCellValue(DAYS.get(i));
                    else if (format.equals("ROTACION_2_SEMANAS")) { row.getCell(3).setCellValue(i / 7 + 1); row.getCell(4).setCellValue(DAYS.get(i % 7)); }
                    else if (format.equals("CICLO_DIAS")) row.getCell(3).setCellValue(i + 1);
                    else { Cell dateCell=row.getCell(3); dateCell.setCellValue(java.sql.Date.valueOf(startsOn.plusDays(i))); dateCell.setCellStyle(dateStyle); }
                    rowIndex++;
                }
                workbook.write(output); return output.toByteArray();
            }
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo descargar la plantilla seleccionada."); }
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
        if ((format.equals("SEMANA_FIJA") || format.equals("ROTACION_2_SEMANAS")) && startsOn.getDayOfWeek() != DayOfWeek.MONDAY) throw bad("Los formatos semanal y de dos semanas deben comenzar un lunes.");
        LocalDate endsOn = startsOn.plusMonths(2).minusDays(1);
        List<Employee> active = activeEmployees();
        Map<String, Employee> byRut = new HashMap<>();
        for (Employee employee : active) {
            String rut = normalizeRut(employee.rut());
            if (rut.isBlank()) throw bad("Hay personal activo sin RUT. Completa sus datos en Personal antes de cargar el horario.");
            if (byRut.put(rut, employee) != null) throw bad("Hay RUT repetidos entre el personal activo. Corrige Personal antes de importar.");
        }
        List<ShiftRow> imported = new ArrayList<>();
        int cycleDays;
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheet("Horario");
            if (sheet == null) throw bad("Falta la hoja Horario. Descarga la plantilla elegida desde Asistencia.");
            if (!format.equals(text(sheet.getRow(1).getCell(1), formatter, evaluator))) throw bad("El archivo no corresponde al formato seleccionado. Descarga esa plantilla y vuelve a completarla.");
            cycleDays = switch (format) {
                case "SEMANA_FIJA" -> 7;
                case "ROTACION_2_SEMANAS" -> 14;
                case "CICLO_DIAS" -> (int) number(sheet.getRow(2).getCell(1), formatter, evaluator);
                case "CALENDARIO_FECHAS" -> (int) ChronoUnit.DAYS.between(startsOn, endsOn) + 1;
                default -> throw bad("Formato de horario no válido.");
            };
            if (cycleDays < 1 || cycleDays > 62) throw bad("El ciclo debe tener entre 1 y 62 días.");
            String[] expectedHeaders = templateHeaders(format);
            Row header = sheet.getRow(6);
            if (header == null) throw bad("No encuentro los encabezados en la fila 7 de la plantilla.");
            for (int c=0;c<expectedHeaders.length;c++) if (!normalizeName(expectedHeaders[c]).equals(normalizeName(text(header.getCell(c),formatter,evaluator)))) throw bad("No cambies los encabezados ni su orden. Descarga una copia nueva de la plantilla.");
            Set<String> seen = new HashSet<>();
            for (int r=7;r<=sheet.getLastRowNum();r++) {
                Row row=sheet.getRow(r); if(row==null) continue;
                String area=normalizeArea(text(row.getCell(0),formatter,evaluator));
                String name=text(row.getCell(1),formatter,evaluator), rut=text(row.getCell(2),formatter,evaluator);
                if(area==null && name.isBlank() && rut.isBlank()) continue;
                if(area==null || name.isBlank() || rut.isBlank()) throw bad("Fila "+(r+1)+": completa Área, Nombre y RUT.");
                Employee employee=byRut.get(normalizeRut(rut));
                if(employee==null) throw bad("El RUT "+rut+" no corresponde a una persona activa registrada en Personal.");
                if(!area.equals(employee.area()) || !normalizeName(employee.name()).equals(normalizeName(name))) throw bad("El nombre, RUT o área de "+name+" no coincide con Personal.");
                int index;
                if(format.equals("SEMANA_FIJA")) index=dayIndex(text(row.getCell(3),formatter,evaluator));
                else if(format.equals("ROTACION_2_SEMANAS")) {
                    int week=(int)number(row.getCell(3),formatter,evaluator); if(week<1||week>2) throw bad("SEMANA_CICLO debe ser 1 o 2.");
                    index=(week-1)*7+dayIndex(text(row.getCell(4),formatter,evaluator));
                } else if(format.equals("CICLO_DIAS")) index=(int)number(row.getCell(3),formatter,evaluator);
                else {
                    LocalDate date=readDate(row.getCell(3),formatter,evaluator);
                    if(date.isBefore(startsOn)||date.isAfter(endsOn)) throw bad("La fecha "+date+" está fuera de la vigencia seleccionada.");
                    index=(int)ChronoUnit.DAYS.between(startsOn,date)+1;
                }
                if(index<1||index>cycleDays) throw bad("El día de ciclo de la fila "+(r+1)+" está fuera del ciclo indicado.");
                if(!seen.add(employee.id()+"|"+index)) throw bad("Hay turnos repetidos para "+employee.name()+" en el mismo día.");
                int shiftCol=(format.equals("SEMANA_FIJA")||format.equals("CICLO_DIAS"))?4:5;
                String shift=text(row.getCell(shiftCol),formatter,evaluator).trim();
                String startText=text(row.getCell(shiftCol+1),formatter,evaluator), endText=text(row.getCell(shiftCol+2),formatter,evaluator);
                int hoursCol=(format.equals("SEMANA_FIJA")||format.equals("CICLO_DIAS"))?9:10;
                double hours=number(row.getCell(hoursCol),formatter,evaluator);
                boolean off=normalizeName(shift).equals("LIBRE"); LocalTime startTime=null,endTime=null;
                if(off) { if(hours!=0||!startText.isBlank()||!endText.isBlank()) throw bad("Un día LIBRE debe tener 0 horas y entrada/salida vacías."); }
                else {
                    startTime=parseClock(startText); endTime=parseClock(endText);
                    String exitDay=normalizeName(text(row.getCell(shiftCol+3),formatter,evaluator));
                    if(endTime.isBefore(startTime) && !exitDay.equals("SIGUIENTE")) throw bad("El turno nocturno de "+employee.name()+" debe indicar SIGUIENTE en DIA_SALIDA.");
                    if(!endTime.isBefore(startTime) && exitDay.equals("SIGUIENTE")) throw bad("DIA_SALIDA solo debe ser SIGUIENTE cuando el turno termina después de medianoche.");
                    if(hours<=0||hours>24||startTime.equals(endTime)) throw bad("Revisa el turno y las horas de "+employee.name()+" en la fila "+(r+1)+".");
                }
                imported.add(new ShiftRow(employee,area,index,1,startTime,endTime,off?"LIBRE":"TRABAJA",hours));
            }
            if(imported.isEmpty()) throw bad("La planilla no tiene turnos. Complétala antes de cargarla.");
            for(Employee employee:active) for(int i=1;i<=cycleDays;i++) if(!seen.contains(employee.id()+"|"+i)) throw bad("Falta un día (incluidos los días LIBRE) para "+employee.name()+", posición "+i+".");
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
        return Map.of("vigenteDesde",startsOn.toString(),"vigenteHasta",endsOn.toString(),"personas",byRut.size(),"turnos",imported.size());
    }

    private static String[] templateHeaders(String format) {
        return switch(format) {
            case "SEMANA_FIJA" -> new String[]{"AREA","NOMBRE","RUT","DIA_SEMANA","TURNO","HORA_ENTRADA","HORA_SALIDA","DIA_SALIDA","PAUSA_MINUTOS","HORAS_PROGRAMADAS"};
            case "ROTACION_2_SEMANAS" -> new String[]{"AREA","NOMBRE","RUT","SEMANA_CICLO","DIA_SEMANA","TURNO","HORA_ENTRADA","HORA_SALIDA","DIA_SALIDA","PAUSA_MINUTOS","HORAS_PROGRAMADAS"};
            case "CICLO_DIAS" -> new String[]{"AREA","NOMBRE","RUT","DIA_CICLO","TURNO","HORA_ENTRADA","HORA_SALIDA","DIA_SALIDA","PAUSA_MINUTOS","HORAS_PROGRAMADAS"};
            case "CALENDARIO_FECHAS" -> new String[]{"AREA","NOMBRE","RUT","FECHA","TURNO","HORA_ENTRADA","HORA_SALIDA","DIA_SALIDA","PAUSA_MINUTOS","HORAS_PROGRAMADAS"};
            default -> throw bad("Formato de horario no válido.");
        };
    }

    private static int dayIndex(String value) {
        String day=normalizeName(value);
        for(int i=0;i<DAYS.size();i++) if(normalizeName(DAYS.get(i)).equals(day)) return i+1;
        try { int n=Integer.parseInt(value.trim()); if(n>=1&&n<=7) return n; } catch(Exception ignored) {}
        throw bad("DIA_SEMANA debe ser un día de lunes a domingo.");
    }
    private static LocalTime parseClock(String value) {
        try { return LocalTime.parse(value.trim().length()==4?"0"+value.trim():value.trim()); }
        catch(Exception ex) { throw bad("Las horas de entrada y salida deben tener formato HH:mm."); }
    }
    private static LocalDate readDate(Cell cell, DataFormatter formatter, org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        if(cell==null) throw bad("Completa la fecha del turno.");
        try { if(org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate(); return LocalDate.parse(text(cell,formatter,evaluator)); }
        catch(Exception ex) { throw bad("FECHA debe tener formato AAAA-MM-DD."); }
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
    private static int storedCycleIndex(ScheduleRow row) { return row.week() <= 2 && row.day() > 1 ? (row.week() - 1) * 7 + row.day() : row.week(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private record Employee(long id, String name, String rut, String area) {}
    private record ShiftRow(Employee employee, String area, int week, int day, LocalTime start, LocalTime end, String state, double hours) {}
    private record ScheduleRow(String area, LocalDate from, LocalDate to, long employeeId, String rut, String name, int week, int day,
                               String start, String end, String state, double hours, String type, int cycleDays) {}
}
