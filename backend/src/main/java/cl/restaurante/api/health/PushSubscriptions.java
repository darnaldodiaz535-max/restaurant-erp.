package cl.restaurante.api.health;

import java.net.URI;
import java.util.Base64;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PushSubscriptions {
    private final JdbcTemplate jdbc;
    public PushSubscriptions(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    // Restrict to established browser push services, never arbitrary user-controlled hosts.
    static void validateEndpoint(String endpoint) {
        try {
            if(endpoint==null || endpoint.length()>2000) throw new IllegalArgumentException();
            URI u=URI.create(endpoint); String h=u.getHost();
            boolean allowed="fcm.googleapis.com".equals(h) || "updates.push.services.mozilla.com".equals(h)
                || (h!=null && (h.endsWith(".push.services.mozilla.com") || h.equals("web.push.apple.com") || h.endsWith(".notify.windows.com")));
            if(!"https".equals(u.getScheme()) || !allowed || u.getUserInfo()!=null || u.getFragment()!=null || (u.getPort()!=-1&&u.getPort()!=443)) throw new IllegalArgumentException();
        } catch(RuntimeException ex) { throw new IllegalArgumentException("El navegador entregó una dirección Push no admitida."); }
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    @Transactional
    public void save(long user,Subscription s) {
        if(s==null||s.keys()==null) throw new IllegalArgumentException("Suscripción incompleta.");
        validateEndpoint(s.endpoint());
        try {
            byte[] publicKey=Base64.getUrlDecoder().decode(s.keys().p256dh());
            if(publicKey.length!=65 || publicKey[0]!=4 || Base64.getUrlDecoder().decode(s.keys().auth()).length!=16) throw new IllegalArgumentException();
            org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256r1").getCurve().decodePoint(publicKey);
        } catch(RuntimeException ex) { throw new IllegalArgumentException("Claves de suscripción inválidas."); }
        jdbc.queryForObject("SELECT ID_USUARIO FROM APP_USUARIO WHERE ID_USUARIO=? FOR UPDATE",Long.class,user);
        String fingerprint=hash(s.endpoint());
        var owners=jdbc.query("SELECT ID_USUARIO FROM APP_PUSH_SUSCRIPCION WHERE ENDPOINT_HASH=?",(rs,n)->rs.getLong(1),fingerprint);
        if(!owners.isEmpty() && owners.getFirst()!=user) throw new ResponseStatusException(HttpStatus.CONFLICT,"Este navegador está vinculado a otra cuenta. Desactiva las notificaciones del dispositivo y vuelve a activarlas.");
        if(jdbc.update("UPDATE APP_PUSH_SUSCRIPCION SET P256DH=?,AUTH=?,ACTUALIZADO_EN=SYSTIMESTAMP WHERE ENDPOINT_HASH=? AND ID_USUARIO=?",s.keys().p256dh(),s.keys().auth(),fingerprint,user)==0) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM APP_PUSH_SUSCRIPCION WHERE ID_USUARIO=?",Integer.class,user)>=10) throw new IllegalArgumentException("Límite de 10 dispositivos por usuario.");
            jdbc.update("INSERT INTO APP_PUSH_SUSCRIPCION(ID_USUARIO,ENDPOINT,ENDPOINT_HASH,P256DH,AUTH) VALUES(?,?,?,?,?)",user,s.endpoint(),fingerprint,s.keys().p256dh(),s.keys().auth());
        }
    }
    public void remove(long user,String endpoint) {
        if(endpoint==null) throw new IllegalArgumentException("Falta la suscripción.");
        jdbc.update("DELETE FROM APP_PUSH_SUSCRIPCION WHERE ID_USUARIO=? AND ENDPOINT_HASH=?",user,hash(endpoint));
    }
    public record Keys(String p256dh,String auth) {}
    public record Subscription(String endpoint,Keys keys) {}
}
