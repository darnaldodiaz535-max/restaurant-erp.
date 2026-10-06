package cl.restaurante.api.health;
import java.io.StringReader;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final NotificationPreferences preferences;
    private final NotificationDelivery delivery;
    public NotificationService(JdbcTemplate jdbc,NotificationPreferences preferences,NotificationDelivery delivery) { this.jdbc=jdbc;this.preferences=preferences;this.delivery=delivery; }
    @org.springframework.transaction.annotation.Transactional
    public void employee(long employeeId,String module,String message) {
        for(long user:jdbc.query("SELECT ID_USUARIO FROM APP_USUARIO WHERE ID_EMPLEADO=? AND ACTIVO='S'",(rs,n)->rs.getLong(1),employeeId)) notify(user,module,message);
    }
    @org.springframework.transaction.annotation.Transactional
    public void administrators(String module,String message) {
        for(long user:jdbc.query("SELECT ID_USUARIO FROM APP_USUARIO WHERE ACTIVO='S' AND ROL IN ('ADMIN','EMPRESA','JEFE_SALON','JEFE_COCINA','JEFE_LOCAL')",(rs,n)->rs.getLong(1))) notify(user,module,message);
    }
    @org.springframework.transaction.annotation.Transactional
public void administratorsWithDetail(
        String module,
        String pushPreview,
        String fullDetail) {
    List<Long> users = jdbc.query(
            "SELECT ID_USUARIO FROM APP_USUARIO "
                    + "WHERE ACTIVO='S' "
                    + "AND ROL IN "
                    + "('ADMIN','EMPRESA','JEFE_SALON','JEFE_COCINA','JEFE_LOCAL')",
            (rs, rowNum) -> rs.getLong(1));

    for (long user : users) {
        notifyWithDetail(user, module, pushPreview, fullDetail);
    }
}
private void notifyWithDetail(
        long user,
        String module,
        String pushPreview,
        String fullDetail) {
    var preferencesForUser = preferences.get(user);
    var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();

    jdbc.update(connection -> {
        var statement = connection.prepareStatement(
                "INSERT INTO APP_NOTIFICACION "
                        + "(ID_USUARIO,MODULO,MENSAJE,DETALLE,LEIDA,VISIBLE) "
                        + "VALUES (?,?,?,?, 'N', 'S')",
                new String[]{"ID_NOTIFICACION"});
        statement.setLong(1, user);
        statement.setString(2, module);
        statement.setString(
                3,
                pushPreview.substring(0, Math.min(pushPreview.length(), 900)));
        statement.setCharacterStream(
                4,
                new StringReader(fullDetail),
                fullDetail.length());
        return statement;
    }, keys);

    delivery.enqueue(keys.getKey().longValue(), user, preferencesForUser);
}
    private void notify(long user,String module,String message) {
        var p=preferences.get(user);
        String safe=message.substring(0,Math.min(message.length(),900));
        var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection->{
            var st=connection.prepareStatement("INSERT INTO APP_NOTIFICACION(ID_USUARIO,MODULO,MENSAJE,VISIBLE) VALUES(?,?,?,?)",new String[]{"ID_NOTIFICACION"});
            st.setLong(1,user);st.setString(2,module);st.setString(3,safe);st.setString(4,p.internal()?"S":"N");return st;
        },keys);
        delivery.enqueue(keys.getKey().longValue(),user,p);
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
