package cl.restaurante.api.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferences {
    private final JdbcTemplate jdbc;
    public NotificationPreferences(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Preferences get(long user) {
        var rows=jdbc.query("SELECT INTERNA,PUSH,CORREO,EMAIL FROM APP_NOTIF_PREFERENCIA WHERE ID_USUARIO=?",
            (rs,n)->new Preferences("S".equals(rs.getString(1)),"S".equals(rs.getString(2)),"S".equals(rs.getString(3)),rs.getString(4)),user);
        return rows.isEmpty()?new Preferences(true,false,false,""):rows.getFirst();
    }
    @Transactional
    public Preferences save(long user,Preferences p) {
        if(p==null) throw new IllegalArgumentException("Completa tus preferencias.");
        String email=p.email()==null?"":p.email().trim();
        if(email.length()>254 || (!email.isEmpty() && !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+")) || (p.mail()&&email.isEmpty()))
            throw new IllegalArgumentException("Escribe un correo válido para recibir avisos.");
        // Serialize preference changes for this account, including first save.
        jdbc.queryForObject("SELECT ID_USUARIO FROM APP_USUARIO WHERE ID_USUARIO=? FOR UPDATE",Long.class,user);
        Object[] args={p.internal()?"S":"N",p.push()?"S":"N",p.mail()?"S":"N",email,user};
        if(jdbc.update("UPDATE APP_NOTIF_PREFERENCIA SET INTERNA=?,PUSH=?,CORREO=?,EMAIL=? WHERE ID_USUARIO=?",args)==0)
            jdbc.update("INSERT INTO APP_NOTIF_PREFERENCIA(INTERNA,PUSH,CORREO,EMAIL,ID_USUARIO) VALUES(?,?,?,?,?)",args);
        return get(user);
    }
    public record Preferences(boolean internal,boolean push,boolean mail,String email) {}
}
