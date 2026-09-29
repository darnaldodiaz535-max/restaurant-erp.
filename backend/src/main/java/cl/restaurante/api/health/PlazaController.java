package cl.restaurante.api.health;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/plazas")
public class PlazaController {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final ActivityService activity;
    public PlazaController(JdbcTemplate jdbc, RoleAccessService access, ActivityService activity) {
        this.jdbc=jdbc; this.access=access; this.activity=activity;
    }
    @GetMapping
    public List<Plaza> list(HttpSession session) {
        access.current(session);
        return jdbc.query("SELECT P.ID_PLAZA,P.CARGO,P.AREA,P.CUPOS,P.ACTIVA,(SELECT COUNT(*) FROM APP_EMPLEADO E WHERE UPPER(TRIM(E.CARGO))=UPPER(TRIM(P.CARGO)) AND "+AreaService.sql("E.AREA")+"=P.AREA AND E.ESTADO='ACTIVO') AS OCUPADOS FROM APP_PLAZA P ORDER BY P.AREA,P.CARGO",
            (rs,n)->new Plaza(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),"S".equals(rs.getString(5)),rs.getInt(6)));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,String> create(@RequestBody Request r,HttpSession session) {
        access.requireOwner(session); validate(r);
        jdbc.update("INSERT INTO APP_PLAZA(CARGO,AREA,CUPOS,ACTIVA) VALUES(?,?,?,?)",r.position().trim(),AreaService.valid(r.area()),r.slots(),r.active()?"S":"N");
        activity.record(session,"CREAR","Personal","Se agregó una plaza: "+r.position().trim());
        return Map.of("status","guardada");
    }
    @PutMapping("/{id}")
    @Transactional
    public Map<String,String> update(@PathVariable long id,@RequestBody Request r,HttpSession session) {
        access.requireOwner(session); validate(r);
        int changed=jdbc.update("UPDATE APP_PLAZA SET CARGO=?,AREA=?,CUPOS=?,ACTIVA=? WHERE ID_PLAZA=?",r.position().trim(),AreaService.valid(r.area()),r.slots(),r.active()?"S":"N",id);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plaza no encontrada.");
        activity.record(session,"ACTUALIZAR","Personal","Se actualizó una plaza: "+r.position().trim());
        return Map.of("status","actualizada");
    }
    private void validate(Request r) {
        if(r==null || r.position()==null || r.position().isBlank() || r.position().trim().length()>80 || r.slots()<1 || r.slots()>9999)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Completa cargo y cupos (1 a 9999).");
        AreaService.valid(r.area());
    }
    public record Request(String position,String area,int slots,boolean active) {}
    public record Plaza(long id,String position,String area,int slots,boolean active,int occupied) {}
}
