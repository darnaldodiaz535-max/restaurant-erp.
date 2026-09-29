package cl.restaurante.api.health;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    public NotificationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void employee(long employeeId,String module,String message) {
        jdbc.update("INSERT INTO APP_NOTIFICACION(ID_USUARIO,MODULO,MENSAJE) SELECT ID_USUARIO,?,? FROM APP_USUARIO WHERE ID_EMPLEADO=? AND ACTIVO='S'",module,message,employeeId);
    }
    public void administrators(String module,String message) {
        jdbc.update("INSERT INTO APP_NOTIFICACION(ID_USUARIO,MODULO,MENSAJE) SELECT ID_USUARIO,?,? FROM APP_USUARIO WHERE ACTIVO='S' AND ROL IN ('ADMIN','EMPRESA','JEFE_SALON','JEFE_COCINA','JEFE_LOCAL')",module,message);
    }
    public List<Long> validateRecipients(List<Long> ids) {
        if(ids==null) return List.of();
        if(ids.size()>200 || ids.stream().anyMatch(id->id==null || id<=0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona destinatarios válidos (máximo 200).");
        var result=ids.stream().distinct().toList();
        for(long id:result) if(jdbc.queryForObject("SELECT COUNT(*) FROM APP_EMPLEADO WHERE ID_EMPLEADO=? AND UPPER(ESTADO)='ACTIVO'",Integer.class,id)==0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Un destinatario ya no está activo.");
        return result;
    }
}
