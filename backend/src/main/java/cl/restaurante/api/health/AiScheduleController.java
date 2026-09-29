package cl.restaurante.api.health;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/horarios/ia")
public class AiScheduleController {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final AiScheduleClient ai;
    private final NotificationService notifications;
    private final ActivityService activity;
    private final ObjectMapper json=new ObjectMapper();
    public AiScheduleController(JdbcTemplate jdbc,RoleAccessService access,AiScheduleClient ai,NotificationService notifications,ActivityService activity) {
        this.jdbc=jdbc;this.access=access;this.ai=ai;this.notifications=notifications;this.activity=activity;
    }
    @GetMapping("/config")
    public Map<String,Boolean> config(HttpSession session) { access.requireOwner(session);return Map.of("configured",ai.configured()); }
    @GetMapping("/propuestas")
    public List<Draft> drafts(HttpSession session) {
        access.requireOwner(session);
        return jdbc.query("SELECT ID_PROPUESTA,FECHA_INICIO,CONTENIDO FROM APP_PROPUESTA_HORARIO WHERE ID_USUARIO=? AND ESTADO='BORRADOR' ORDER BY CREADO_EN DESC FETCH FIRST 10 ROWS ONLY",
            (rs,n)->new Draft(rs.getString(1),rs.getDate(2).toLocalDate().toString(),json.readValue(rs.getString(3),Proposal.class)),access.current(session).id());
    }
    @PostMapping("/propuestas")
    public Draft generate(@RequestBody Generate request,HttpSession session) {
        access.requireOwner(session);
        if(request==null || request.instructions()==null || request.instructions().isBlank() || request.instructions().length()>6000) throw bad("Escribe instrucciones de hasta 6000 caracteres.");
        LocalDate start=date(request.startDate());
        var employees=employees();
        if(employees.isEmpty() || employees.size()>200) throw bad("La generación admite de 1 a 200 trabajadores activos con un área válida.");
        var existing=existing(start);
        String context=json.writeValueAsString(Map.of("startDate",start.toString(),"endDate",start.plusDays(6).toString(),"instructions",request.instructions(),"employees",employees,"existingShifts",existing));
        Proposal proposal;
        try { proposal=json.readValue(ai.generate(context),Proposal.class); }
        catch(ResponseStatusException ex) { throw ex; }
        catch(RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"La IA devolvió un formato no válido. Inténtalo nuevamente."); }
        validate(start,proposal,employees,existing);
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO APP_PROPUESTA_HORARIO(ID_PROPUESTA,ID_USUARIO,FECHA_INICIO,CONTENIDO) VALUES(?,?,?,?)",id,access.current(session).id(),java.sql.Date.valueOf(start),json.writeValueAsString(proposal));
        return new Draft(id,start.toString(),proposal);
    }
    @PostMapping("/propuestas/{id}/publicar")
    @Transactional
    public Map<String,String> publish(@PathVariable String id,@RequestBody Proposal proposal,HttpSession session) {
        access.requireOwner(session);
        var drafts=jdbc.query("SELECT FECHA_INICIO,ESTADO FROM APP_PROPUESTA_HORARIO WHERE ID_PROPUESTA=? AND ID_USUARIO=? FOR UPDATE",(rs,n)->new State(rs.getDate(1).toLocalDate(),rs.getString(2)),id,access.current(session).id());
        if(drafts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Propuesta no encontrada.");
        var state=drafts.getFirst();
        if("PUBLICADO".equals(state.status())) return Map.of("status","publicado");
        // Serialize publication per employee; a duplicate click cannot insert the draft twice.
        if(proposal==null || proposal.shifts()==null || proposal.shifts().isEmpty() || proposal.shifts().size()>1400) throw bad("Revisa los turnos de la propuesta.");
        if(proposal.shifts().stream().anyMatch(Objects::isNull)) throw bad("Turno no válido.");
        for(long employee:proposal.shifts().stream().map(Shift::employeeId).distinct().sorted().toList())
            jdbc.query("SELECT ID_EMPLEADO FROM APP_EMPLEADO WHERE ID_EMPLEADO=? FOR UPDATE",(rs,n)->rs.getLong(1),employee);
        var employees=employees();
        validate(state.start(),proposal,employees,existing(state.start()));
        Map<Long,Employee> byId=new HashMap<>();employees.forEach(e->byId.put(e.id(),e));
        for(var shift:proposal.shifts()) jdbc.update("INSERT INTO APP_TURNO(EMPLEADO_ID,FECHA,HORA_INICIO,HORA_TERMINO,AREA) VALUES(?,?,?,?,?)",shift.employeeId(),java.sql.Date.valueOf(shift.date()),shift.start(),shift.end(),byId.get(shift.employeeId()).area());
        jdbc.update("UPDATE APP_PROPUESTA_HORARIO SET ESTADO='PUBLICADO',CONTENIDO=? WHERE ID_PROPUESTA=?",json.writeValueAsString(proposal),id);
        for(long employee:proposal.shifts().stream().map(Shift::employeeId).distinct().toList()) notifications.employee(employee,"Horarios","Se publicó tu horario de la semana que comienza el "+state.start()+".");
        activity.record(session,"CREAR","Horarios","Se publicó un horario revisado por administración.");
        return Map.of("status","publicado");
    }
    private List<Employee> employees() {
        return jdbc.query("SELECT ID_EMPLEADO,NOMBRE,CARGO,AREA FROM APP_EMPLEADO WHERE UPPER(ESTADO)='ACTIVO' ORDER BY ID_EMPLEADO",(rs,n)->new Employee(rs.getLong(1),rs.getString(2),rs.getString(3),AreaService.normalize(rs.getString(4))))
            .stream().filter(e->AreaService.AREAS.contains(e.area())).toList();
    }
    private List<Shift> existing(LocalDate start) {
        return jdbc.query("SELECT EMPLEADO_ID,FECHA,HORA_INICIO,HORA_TERMINO FROM APP_TURNO WHERE FECHA>=? AND FECHA<?",(rs,n)->new Shift(rs.getLong(1),rs.getDate(2).toLocalDate().toString(),rs.getString(3),rs.getString(4)),java.sql.Date.valueOf(start),java.sql.Date.valueOf(start.plusDays(7)));
    }
    private void validate(LocalDate first,Proposal p,List<Employee> employees,List<Shift> existing) {
        if(p==null || p.shifts()==null || p.shifts().isEmpty() || p.shifts().size()>1400) throw bad("La propuesta debe contener entre 1 y 1400 turnos.");
        if(p.warnings()==null || p.warnings().size()>100 || p.warnings().stream().anyMatch(w->w==null || w.length()>2000)) throw bad("Avisos de propuesta no válidos.");
        Set<Long> ids=new HashSet<>();employees.forEach(e->ids.add(e.id()));
        List<Shift> checked=new ArrayList<>();
        for(var s:p.shifts()) {
            if(s==null || !ids.contains(s.employeeId())) throw bad("La propuesta incluye un trabajador inexistente, inactivo o sin área válida.");
            LocalDate day=date(s.date());
            if(day.isBefore(first) || day.isAfter(first.plusDays(6))) throw bad("Los turnos deben pertenecer a los siete días seleccionados.");
            LocalTime from=time(s.start()),to=time(s.end());
            if(!to.isAfter(from) || Duration.between(from,to).toMinutes()>720) throw bad("Cada turno debe finalizar el mismo día, después del inicio, y durar como máximo 12 horas.");
            for(var other:checked) if(overlap(s,other)) throw bad("Hay turnos superpuestos para un trabajador.");
            for(var other:existing) if(s.employeeId()==other.employeeId() && s.date().equals(other.date())) {
                // Invalid legacy hours must be reviewed, never silently overwritten.
                if(!time(other.end()).isAfter(time(other.start()))) throw bad("Revisa el horario existente de este trabajador antes de publicar.");
                if(overlap(s,other)) throw bad("Un turno se superpone con un horario ya publicado. Corrige la propuesta.");
            }
            checked.add(s);
        }
    }
    private boolean overlap(Shift a,Shift b) { return a.employeeId()==b.employeeId() && a.date().equals(b.date()) && time(a.start()).isBefore(time(b.end())) && time(b.start()).isBefore(time(a.end())); }
    private LocalDate date(String value) { try { if(value==null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw bad("Fecha no válida.");return LocalDate.parse(value); } catch(java.time.DateTimeException ex) { throw bad("Fecha no válida."); } }
    private LocalTime time(String value) { try { if(value==null || !value.matches("\\d{2}:\\d{2}")) throw bad("Usa horas HH:mm.");return LocalTime.parse(value); } catch(java.time.DateTimeException ex) { throw bad("Hora no válida."); } }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    public record Generate(String instructions,String startDate) {}
    public record Shift(long employeeId,String date,String start,String end) {}
    public record Proposal(List<Shift> shifts,List<String> warnings) {}
    public record Draft(String id,String startDate,Proposal proposal) {}
    private record Employee(long id,String name,String position,String area) {}
    private record State(LocalDate start,String status) {}
}
