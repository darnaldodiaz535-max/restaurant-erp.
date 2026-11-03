package cl.restaurante.api.health;

import java.io.IOException;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/checkin/gestion")
public class AreaCheckinController {
    private final RoleAccessService access;
    private final AreaCheckinService service;
    public AreaCheckinController(RoleAccessService access, AreaCheckinService service) { this.access=access; this.service=service; }

    @GetMapping("/areas/{area}")
    public AreaCheckinService.Day day(@PathVariable String area, @RequestParam(required=false) String fecha, HttpSession session) {
        return service.day(access.current(session), area, fecha == null ? CheckinService.operationalDate() : date(fecha));
    }

    @PostMapping("/tareas/{id}/hecha")
    public java.util.Map<String,String> complete(@PathVariable long id, HttpSession session) { service.complete(access.current(session),id,null,null,null); return java.util.Map.of("status","guardado"); }

    @PostMapping(value="/tareas/{id}/foto", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public java.util.Map<String,String> photo(@PathVariable long id, @RequestParam MultipartFile foto, HttpSession session) throws IOException {
        var user=access.current(session);
        if (foto.isEmpty() || foto.getSize()>4L*1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Adjunta una foto de hasta 4 MB.");
        service.complete(user,id,foto.getBytes(),foto.getContentType(),foto.getOriginalFilename());
        return java.util.Map.of("status","guardado");
    }

    @GetMapping("/fotos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable long id, HttpSession session) {
        var photo=service.photo(access.current(session),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.type()))
                .header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+photo.filename()+"\"")
                .cacheControl(CacheControl.noStore()).body(photo.bytes());
    }

    @GetMapping("/semanal")
    public List<AreaCheckinService.Report> weekly(@RequestParam(required=false) String area, @RequestParam(required=false) String semana, HttpSession session) {
        return service.weekly(access.current(session),area,semana==null ? CheckinService.operationalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : date(semana));
    }

    @GetMapping("/semanas")
    public List<String> weeks(@RequestParam String area, HttpSession session) { return service.weeks(access.current(session),area); }

    @GetMapping("/observaciones")
    public List<AreaCheckinService.Suggestion> suggestions(@RequestParam String area, HttpSession session) { return service.suggestions(access.current(session),area); }

    @PostMapping("/observaciones")
    public java.util.Map<String,String> suggest(@RequestBody Observation observation, HttpSession session) { service.suggest(access.current(session),observation.area(),observation.message()); return java.util.Map.of("status","enviado"); }

    private static LocalDate date(String value) {
        try { return LocalDate.parse(value); }
        catch (RuntimeException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Usa una fecha válida AAAA-MM-DD."); }
    }
    public record Observation(String area, String message) {}
}
