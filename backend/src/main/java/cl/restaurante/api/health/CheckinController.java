package cl.restaurante.api.health;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {
    private static final long MAX_PHOTO_BYTES = 4L * 1024 * 1024;
    private final RoleAccessService access;
    private final CheckinService checkin;

    public CheckinController(RoleAccessService access, CheckinService checkin) {
        this.access = access;
        this.checkin = checkin;
    }

    @GetMapping("/areas/{area}/personas")
    public List<CheckinService.RosterPerson> roster(
            @PathVariable String area,
            HttpSession session) {
        return checkin.roster(area, access.current(session));
    }

    @GetMapping("/me")
    public CheckinService.DayView myToday(HttpSession session) {
        return checkin.myToday(access.current(session));
    }

    @GetMapping("/me/dia")
    public CheckinService.DayView myDay(
            @RequestParam String fecha,
            HttpSession session) {
        return checkin.myDay(access.current(session), parseDate(fecha));
    }

    @PutMapping("/me/libre")
    public CheckinService.DayView markFree(HttpSession session) {
        return checkin.markFree(access.current(session));
    }

    @PutMapping("/me/trabajando")
    public CheckinService.DayView markWorking(HttpSession session) {
        return checkin.markWorking(access.current(session));
    }

    @PostMapping("/tareas/{id}/hecha")
    public CheckinService.DayView completeTask(
            @PathVariable long id,
            HttpSession session) {
        return checkin.completeTask(access.current(session), id);
    }

    @PostMapping(value = "/tareas/{id}/foto",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CheckinService.DayView submitPhoto(
            @PathVariable long id,
            @RequestParam MultipartFile foto,
            HttpSession session) throws IOException {
        if (foto == null || foto.isEmpty() || foto.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Adjunta una foto JPG, PNG o WebP de hasta 4 MB.");
        }
        return checkin.submitPhoto(
                access.current(session),
                id,
                foto.getBytes(),
                foto.getContentType(),
                foto.getOriginalFilename());
    }

    @GetMapping("/fotos/{id}")
    public ResponseEntity<byte[]> photo(
            @PathVariable long id,
            HttpSession session) {
        CheckinService.Photo photo = checkin.photo(access.current(session), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.type()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + photo.filename() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(photo.bytes());
    }

    @GetMapping("/me/semanal")
    public CheckinService.WeeklyPerson myWeekly(
            @RequestParam(required = false) String semana,
            HttpSession session) {
        LocalDate start = semana == null
                ? defaultWeek() : parseDate(semana);
        return checkin.myWeekly(access.current(session), start);
    }

    @GetMapping("/me/tendencia")
    public List<CheckinService.WeeklyPoint> myTrend(HttpSession session) {
        return checkin.myTrend(access.current(session));
    }

    @GetMapping("/admin/area/{area}")
    public List<CheckinService.AdminDayPerson> adminDay(
            @PathVariable String area,
            @RequestParam(required = false) String fecha,
            HttpSession session) {
        LocalDate date = fecha == null
                ? CheckinService.operationalDate() : parseDate(fecha);
        return checkin.adminDay(access.current(session), area, date);
    }

    @GetMapping("/admin/semanal")
    public CheckinService.AdminWeekly adminWeekly(
            @RequestParam(required = false) String semana,
            HttpSession session) {
        LocalDate start = semana == null
                ? defaultWeek() : parseDate(semana);
        return checkin.adminWeekly(access.current(session), start);
    }

    @GetMapping("/admin/tendencia")
    public List<CheckinService.TrendEmployee> adminTrend(HttpSession session) {
        return checkin.adminTrend(access.current(session));
    }

    private static LocalDate defaultWeek() {
        return CheckinService.operationalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Usa una fecha válida en formato AAAA-MM-DD.");
        }
    }
}
