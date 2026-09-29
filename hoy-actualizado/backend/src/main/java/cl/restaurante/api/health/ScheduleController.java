package cl.restaurante.api.health;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/horarios")
public class ScheduleController {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;
    private final NotificationService notifications;
    public ScheduleController(JdbcTemplate jdbc,RoleAccessService access,ActivityService activity,NotificationService notifications) {
        this.notifications=notifications;
        this.jdbc=jdbc; this.access=access; this.activity=activity;
    }
    @GetMapping
    public List<Schedule> list(HttpSession session) {
        access.current(session);
        return jdbc.query("SELECT ID_HORARIO,TITULO,NOMBRE_ARCHIVO,CREADO_EN FROM APP_HORARIO_IMAGEN ORDER BY CREADO_EN DESC,ID_HORARIO DESC",
            (rs,n)->new Schedule(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toLocalDateTime().toString(),recipients(rs.getLong(1))));
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,String> upload(@RequestParam String title,@RequestParam MultipartFile image,
            @RequestParam(required=false) Long replaceId,@RequestParam(required=false) List<Long> employeeIds,HttpSession session) throws IOException {
        access.requireOwner(session);
        if(title==null || title.isBlank() || title.trim().length()>160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Escribe un título de hasta 160 caracteres.");
        var oldRecipients=replaceId==null?List.<Long>of():recipients(replaceId);
        var recipients=employeeIds==null?oldRecipients:notifications.validateRecipients(employeeIds);
        byte[] bytes=image.getBytes(); String type=ImageUpload.validate(bytes,image.getContentType());
        String name=ImageUpload.safeName(image.getOriginalFilename());
        long author=access.current(session).id();
        long scheduleId=0;
        if(replaceId==null) {
            var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO APP_HORARIO_IMAGEN(TITULO,NOMBRE_ARCHIVO,TIPO_CONTENIDO,IMAGEN,CREADO_POR) VALUES(?,?,?,?,?)",new String[]{"ID_HORARIO"});statement.setString(1,title.trim());statement.setString(2,name);statement.setString(3,type);statement.setBytes(4,bytes);statement.setLong(5,author);return statement;},keys);
            scheduleId=keys.getKey().longValue();
        }
        else if(jdbc.update("UPDATE APP_HORARIO_IMAGEN SET TITULO=?,NOMBRE_ARCHIVO=?,TIPO_CONTENIDO=?,IMAGEN=?,CREADO_POR=?,CREADO_EN=SYSTIMESTAMP WHERE ID_HORARIO=?",title.trim(),name,type,bytes,author,replaceId)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Horario no encontrado.");
        long savedId=replaceId!=null?replaceId:scheduleId;
        jdbc.update("DELETE FROM APP_HORARIO_DESTINATARIO WHERE ID_HORARIO=?",savedId);
        for(long employee:recipients) jdbc.update("INSERT INTO APP_HORARIO_DESTINATARIO(ID_HORARIO,ID_EMPLEADO) VALUES(?,?)",savedId,employee);
        for(long employee:oldRecipients) if(!recipients.contains(employee)) notifications.employee(employee,"Horarios","Cambió la asignación de un horario en imagen que te correspondía.");
        for(long employee:recipients) notifications.employee(employee,"Horarios","Se publicó o reemplazó tu horario: "+title.trim());
        activity.record(session,replaceId==null?"CREAR":"ACTUALIZAR","Horarios",title.trim());
        return Map.of("status","guardado");
    }
    @GetMapping("/{id}/imagen")
    public ResponseEntity<byte[]> image(@PathVariable long id,HttpSession session) {
        access.current(session);
        var images=jdbc.query("SELECT IMAGEN,TIPO_CONTENIDO FROM APP_HORARIO_IMAGEN WHERE ID_HORARIO=?",(rs,n)->new Picture(rs.getBytes(1),rs.getString(2)),id);
        if(images.isEmpty()) return ResponseEntity.notFound().build();
        var picture=images.getFirst();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(picture.type()))
            .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(picture.bytes());
    }
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable long id,HttpSession session) {
        access.requireOwner(session);
        var affected=recipients(id);
        jdbc.update("DELETE FROM APP_HORARIO_DESTINATARIO WHERE ID_HORARIO=?",id);
        if(jdbc.update("DELETE FROM APP_HORARIO_IMAGEN WHERE ID_HORARIO=?",id)==0) return ResponseEntity.notFound().build();
        for(long employee:affected) notifications.employee(employee,"Horarios","Se eliminó un horario en imagen que te correspondía.");
        activity.record(session,"ELIMINAR","Horarios","Se eliminó el horario #"+id);
        return ResponseEntity.noContent().build();
    }
    private List<Long> recipients(long id) { return jdbc.query("SELECT ID_EMPLEADO FROM APP_HORARIO_DESTINATARIO WHERE ID_HORARIO=?",(rs,n)->rs.getLong(1),id); }
    public record Schedule(long id,String title,String filename,String createdAt,List<Long> employeeIds) {}
    private record Picture(byte[] bytes,String type) {}
}
