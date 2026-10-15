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
        requireAdmin(session);
        return AttendanceScheduleService.templateOptions();
    }

    @GetMapping("/plantilla/{formato}.xlsx")
    public ResponseEntity<byte[]> template(@PathVariable String formato,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteDesde,
                                           @RequestParam String area,
                                           HttpSession session) {
        requireAdmin(session);
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
        RoleAccessService.User user = requireAdmin(session);
        return schedules.importWorkbook(workbook, formato, startsOn, area, user.id());
    }

    @GetMapping("/dia")
    public List<Map<String, Object>> scheduleForDay(@RequestParam(required = false) String fecha, HttpSession session) {
        requireAdmin(session);
        LocalDate date = fecha == null || fecha.isBlank() ? LocalDate.now(java.time.ZoneId.of("America/Santiago")) : LocalDate.parse(fecha);
        return schedules.scheduleForDate(date);
    }

    private RoleAccessService.User requireAdmin(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!List.of("ADMIN", "EMPRESA").contains(user.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el administrador o jefe de empresa puede consultar o actualizar las planillas de asistencia.");
        return user;
    }
}
