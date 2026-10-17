package cl.restaurante.api.health;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/asistencia/horarios")
public class AttendanceScheduleController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final AttendanceScheduleService schedules;
    private final RoleAccessService access;

    public AttendanceScheduleController(AttendanceScheduleService schedules, RoleAccessService access) {
        this.schedules = schedules;
        this.access = access;
    }

    @GetMapping("/plantillas")
    public List<Map<String, String>> templates(HttpSession session) {
        requireScheduleManager(session, null);
        return AttendanceScheduleService.templateOptions();
    }

    @GetMapping("/plantilla/{formato}.xlsx")
    public ResponseEntity<byte[]> template(@PathVariable String formato,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteDesde,
                                           @RequestParam String area,
                                           HttpSession session) {
        requireScheduleManager(session, area);
        byte[] content = schedules.template(formato, vigenteDesde, area);
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + schedules.templateFileName(formato, area))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(content);
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importSchedules(@RequestParam("planilla") MultipartFile workbook,
                                               @RequestParam("formato") String formato,
                                               @RequestParam("vigenteDesde") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
                                               @RequestParam("area") String area,
                                               HttpSession session) throws IOException {
        RoleAccessService.User user = requireScheduleManager(session, area);
        return schedules.importWorkbook(workbook, formato, startsOn, area, user.id());
    }

    @PostMapping(value = "/previsualizar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> preview(@RequestParam("planilla") MultipartFile workbook,
                                       @RequestParam String area,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteDesde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteHasta,
                                       HttpSession session) throws IOException {
        requireScheduleManager(session, area);
        return schedules.previewWorkbook(workbook, area, vigenteDesde, vigenteHasta);
    }

    @PostMapping(value = "/confirmar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> confirm(@RequestBody Map<String, Object> request, HttpSession session) {
        String area = String.valueOf(request == null ? "" : request.getOrDefault("area", ""));
        RoleAccessService.User user = requireScheduleManager(session, area);
        return schedules.confirmPreview(request, user.id());
    }

    @GetMapping("/mi-horario")
    public Map<String, Object> mySchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(defaultValue = "14") int dias,
            HttpSession session) {
        RoleAccessService.User user = access.current(session);
        LocalDate start = desde == null
                ? LocalDate.now(java.time.ZoneId.of("America/Santiago")).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                : desde;
        if (dias < 1 || dias > 366) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período debe incluir entre 1 y 366 días.");
        return schedules.scheduleForEmployee(user.employeeId(), start, dias);
    }

    @GetMapping("/dia")
    public List<Map<String, Object>> scheduleForDay(@RequestParam(required = false) String fecha, HttpSession session) {
        requireAdmin(session);
        LocalDate date = fecha == null || fecha.isBlank() ? LocalDate.now(java.time.ZoneId.of("America/Santiago")) : LocalDate.parse(fecha);
        return schedules.scheduleForDate(date);
    }

    private RoleAccessService.User requireAdmin(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!access.isScheduleAdmin(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administración puede consultar el calendario completo de asistencia.");
        return user;
    }

    private RoleAccessService.User requireScheduleManager(HttpSession session, String requestedArea) {
        RoleAccessService.User user = access.current(session);
        if (!access.canManageAttendanceSchedule(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para cargar horarios de asistencia.");
        String assignedArea = access.scheduleArea(user);
        if (assignedArea != null && requestedArea != null && !requestedArea.isBlank()
                && !assignedArea.equals(RoleAccessService.normalizeScheduleArea(requestedArea))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes cargar horarios del área " + assignedArea + ".");
        }
        if (assignedArea != null && requestedArea != null && requestedArea.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona el área asignada a tu jefatura.");
        return user;
    }
}
