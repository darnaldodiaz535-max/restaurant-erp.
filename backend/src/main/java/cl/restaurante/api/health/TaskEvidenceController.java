package cl.restaurante.api.health;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/evidencias")
public class TaskEvidenceController {
    private static final long MAX_PHOTO_BYTES = 4 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;
    private final NotificationService notifications;

    public TaskEvidenceController(JdbcTemplate jdbc, RoleAccessService access, ActivityService activity, NotificationService notifications) {
        this.notifications=notifications;
        this.jdbc = jdbc; this.access = access; this.activity = activity;
    }

    @GetMapping
    public List<Evidence> list(HttpSession session, @RequestParam String area) {
        RoleAccessService.User user = access.current(session);
        String selected=new AreaService(jdbc,access).requireView(session,area);
        String sql = "SELECT V.ID_EVIDENCIA, V.ID_TAREA, T.TAREA, E.NOMBRE, V.NOMBRE_ARCHIVO, V.ESTADO, V.CREADO_EN, V.RESULTADO " +
                "FROM APP_EVIDENCIA_TAREA V JOIN APP_TAREA_DIARIA T ON T.ID_TAREA=V.ID_TAREA " +
                "JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=V.ID_EMPLEADO";
        sql += " WHERE T.AREA=?";
        sql += " ORDER BY V.CREADO_EN DESC";
        return jdbc.query(sql, (rs, n) -> new Evidence(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime().toString().replace('T', ' '),rs.getString(8)), selected);
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submit(@RequestParam long taskId, @RequestParam MultipartFile photo, HttpSession session) throws IOException {
        RoleAccessService.User user = access.current(session);
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES || photo.getContentType() == null || !List.of("image/jpeg", "image/png", "image/webp").contains(photo.getContentType()))
            return ResponseEntity.badRequest().body(Map.of("error", "Adjunta una foto JPG, PNG o WebP de hasta 4 MB."));
        List<String> tasks = jdbc.query("SELECT TAREA FROM APP_TAREA_DIARIA WHERE ID_TAREA=? AND RESPONSABLE_ID=? AND AREA=? AND UPPER(ESTADO) NOT IN ('EN_REVISION','COMPLETADA','COMPLETADO','FINALIZADA','FINALIZADO') FOR UPDATE",
                (rs, n) -> rs.getString(1), taskId, user.employeeId(), new AreaService(jdbc,access).employeeArea(user.employeeId()));
        if (tasks.isEmpty()) return ResponseEntity.status(403).body(Map.of("error", "Esa tarea no está asignada a tu usuario."));
        byte[] bytes = photo.getBytes();
        ImageUpload.validate(bytes, photo.getContentType());
        jdbc.update("INSERT INTO APP_EVIDENCIA_TAREA (ID_TAREA, ID_EMPLEADO, NOMBRE_ARCHIVO, TIPO_CONTENIDO, FOTO) VALUES (?, ?, ?, ?, ?)",
                taskId, user.employeeId(), safeName(photo.getOriginalFilename()), photo.getContentType(), bytes);
        jdbc.update("UPDATE APP_TAREA_DIARIA SET ESTADO='EN_REVISION' WHERE ID_TAREA=? AND RESPONSABLE_ID=?", taskId, user.employeeId());
        notifications.administrators("Tareas diarias",user.name()+" envió una evidencia para revisión: "+tasks.getFirst());
        activity.record(session, "ENVIAR_TAREA", "Tareas diarias", "Tarea realizada, pendiente de revisión: " + tasks.getFirst());
        return ResponseEntity.status(201).body(Map.of("status", "evidencia enviada"));
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> photo(@PathVariable long id, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        List<Photo> photos = jdbc.query("SELECT V.FOTO, V.TIPO_CONTENIDO, V.NOMBRE_ARCHIVO, V.ID_EMPLEADO " +
                        "FROM APP_EVIDENCIA_TAREA V WHERE V.ID_EVIDENCIA=?",
                (rs, n) -> new Photo(rs.getBytes(1), rs.getString(2), rs.getString(3), rs.getLong(4)), id);
        if (photos.isEmpty()) return ResponseEntity.notFound().build();
        Photo photo = photos.getFirst();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.type()))
                .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + photo.name().replace("\"", "") + "\"")
                .body(photo.bytes());
    }

    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id, HttpSession session) {
        RoleAccessService.User user = access.current(session);
        if (!(access.isOwner(user) || access.isSalon(user))) return ResponseEntity.status(403).body(Map.of("error", "Solo el jefe de empresa o el jefe de salón puede borrar una evidencia."));
        Long taskId = jdbc.query("SELECT ID_TAREA FROM APP_EVIDENCIA_TAREA WHERE ID_EVIDENCIA=?", rs -> rs.next() ? rs.getLong(1) : null, id);
        if (taskId == null) return ResponseEntity.notFound().build();
        int deleted = jdbc.update("DELETE FROM APP_EVIDENCIA_TAREA WHERE ID_EVIDENCIA=?", id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM APP_EVIDENCIA_TAREA WHERE ID_TAREA=?", Integer.class, taskId) == 0) {
            int completed=jdbc.update("UPDATE APP_TAREA_DIARIA SET ESTADO='COMPLETADA' WHERE ID_TAREA=? AND UPPER(ESTADO)='EN_REVISION'", taskId);
            if(completed>0) activity.record(session,"COMPLETAR_TAREA","Tareas diarias","Se completó la tarea #"+taskId+" tras revisar su evidencia");
        }
        if (deleted == 0) return ResponseEntity.notFound().build();
        activity.record(session, "ELIMINAR", "Tareas diarias", "Se eliminó una evidencia fotográfica después de revisarla");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/revisar")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> review(@PathVariable long id, @RequestParam(defaultValue="true") boolean approved, HttpSession session) {
        var user=access.current(session);
        if (!access.isManager(user)) return ResponseEntity.status(403).build();
        var tasks=jdbc.query("SELECT T.ID_TAREA,T.AREA,T.TAREA,T.ESTADO,T.RESPONSABLE_ID,V.ID_EMPLEADO,V.ESTADO FROM APP_EVIDENCIA_TAREA V JOIN APP_TAREA_DIARIA T ON T.ID_TAREA=V.ID_TAREA WHERE V.ID_EVIDENCIA=? FOR UPDATE",
            (rs,n)->new ReviewTask(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getLong(6),rs.getString(7)),id);
        if(tasks.isEmpty()) return ResponseEntity.notFound().build();
        var task=tasks.getFirst();
        new AreaService(jdbc,access).requireView(session,task.area());
        if ("REVISADA".equals(task.evidenceState())) return ResponseEntity.ok(Map.of("status","revisada"));
        if(jdbc.queryForObject("SELECT COUNT(*) FROM APP_EVIDENCIA_TAREA WHERE ID_TAREA=? AND ID_EVIDENCIA>?",Integer.class,task.id(),id)>0)
            return ResponseEntity.status(409).body(Map.of("error","Hay una evidencia más reciente. Revisa la última foto."));
        if (!"EN_REVISION".equalsIgnoreCase(task.state()) || !(task.assignee()==task.sender()))
            return ResponseEntity.status(409).body(Map.of("error","La tarea cambió desde que se envió esta evidencia. Revisa la asignación y solicita una nueva foto."));
        int changed=jdbc.update("UPDATE APP_EVIDENCIA_TAREA SET ESTADO='REVISADA',RESULTADO=? WHERE ID_EVIDENCIA=? AND ESTADO='ENVIADA'",approved?"APROBADA":"RECHAZADA",id);
        if(changed>0) {
            jdbc.update("UPDATE APP_TAREA_DIARIA SET ESTADO=? WHERE ID_TAREA=?",approved?"COMPLETADA":"PENDIENTE",task.id());
            notifications.employee(task.assignee(),"Tareas diarias",(approved?"Se aprobó y completó tu tarea: ":"La evidencia fue rechazada. Envía una nueva foto de tu tarea: ")+task.title());
            notifications.administrators("Tareas diarias",(approved?"Se aprobó la tarea: ":"Se rechazó la evidencia de: ")+task.title());
            activity.record(session,approved?"COMPLETAR_TAREA":"RECHAZAR_EVIDENCIA","Tareas diarias","Se revisó la tarea: " + task.title());
        }
        return ResponseEntity.ok(Map.of("status","revisada"));
    }

    private record ReviewTask(long id,String area,String title,String state,long assignee,long sender,String evidenceState) {}
    private String safeName(String name) {
        String value = name == null ? "foto" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    public record Evidence(long id, long taskId, String task, String employee, String filename, String status, String createdAt,String result) {}
    private record Photo(byte[] bytes, String type, String name, long employeeId) {}
}

