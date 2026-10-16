package cl.restaurante.api.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.List;

@Service
public class NotificationPreferences {
    public static final List<String> MODULES = List.of(
        "Personal", "Asistencia", "Horarios", "Tareas diarias", "Check-in Diario", "Cierre de turno",
        "Inventario", "Cocina", "Salón", "Barra", "Copería", "Producción",
        "Recetas", "Reservas", "Limpieza", "Incidencias", "Compras", "Mermas",
        "Control sanitario", "Reportes"
    );
    private final JdbcTemplate jdbc;
    public NotificationPreferences(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Preferences get(long user) {
        var rows=jdbc.query("SELECT INTERNA,PUSH,CORREO,EMAIL FROM APP_NOTIF_PREFERENCIA WHERE ID_USUARIO=?",
            (rs,n)->new Preferences("S".equals(rs.getString(1)),"S".equals(rs.getString(2)),"S".equals(rs.getString(3)),rs.getString(4)),user);
        return rows.isEmpty()?new Preferences(true,false,false,""):rows.getFirst();
    }
    public List<String> getModules(long user) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM APP_NOTIF_MODULO_PREF WHERE ID_USUARIO=?", Integer.class, user);
        if (count == null || count == 0) return MODULES;
        var disabled = jdbc.query("SELECT MODULO FROM APP_NOTIF_MODULO_PREF WHERE ID_USUARIO=? AND HABILITADA='N'",
            (rs,n)->rs.getString(1), user);
        return MODULES.stream().filter(module -> !disabled.contains(module)).toList();
    }
    public boolean isModuleEnabled(long user, String module) {
        String canonical = canonicalModule(module);
        if (canonical == null) return true;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM APP_NOTIF_MODULO_PREF WHERE ID_USUARIO=? AND MODULO=?", Integer.class, user, canonical);
        if (count == null || count == 0) return true;
        Integer enabled = jdbc.queryForObject("SELECT COUNT(*) FROM APP_NOTIF_MODULO_PREF WHERE ID_USUARIO=? AND MODULO=? AND HABILITADA='S'", Integer.class, user, canonical);
        return enabled != null && enabled > 0;
    }
    @Transactional
    public List<String> saveModules(long user, List<String> selected) {
        if (selected == null || selected.stream().anyMatch(module -> !MODULES.contains(module)))
            throw new IllegalArgumentException("Selecciona apartados válidos para las notificaciones.");
        jdbc.queryForObject("SELECT ID_USUARIO FROM APP_USUARIO WHERE ID_USUARIO=? FOR UPDATE", Long.class, user);
        jdbc.update("DELETE FROM APP_NOTIF_MODULO_PREF WHERE ID_USUARIO=?", user);
        for (String module : MODULES) jdbc.update(
            "INSERT INTO APP_NOTIF_MODULO_PREF(ID_USUARIO,MODULO,HABILITADA) VALUES(?,?,?)",
            user, module, selected.contains(module) ? "S" : "N");
        return List.copyOf(selected);
    }
    private String canonicalModule(String value) {
        if (value == null || value.isBlank()) return null;
        String key = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase();
        return MODULES.stream().filter(module -> Normalizer.normalize(module, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase().equals(key)).findFirst().orElse(null);
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
