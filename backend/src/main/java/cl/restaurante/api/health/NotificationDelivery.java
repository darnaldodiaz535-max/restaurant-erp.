package cl.restaurante.api.health;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationDelivery {
    private final JdbcTemplate jdbc;
    private final NotificationPreferences preferences;
    private final NotificationTransport transport;
    private final ObjectMapper json;
    public NotificationDelivery(JdbcTemplate jdbc,NotificationPreferences preferences,NotificationTransport transport,ObjectMapper json) {
        this.jdbc=jdbc;this.preferences=preferences;this.transport=transport;this.json=json;
    }
    /** Called inside the same business transaction as APP_NOTIFICACION. No network before commit. */
    public void enqueue(long notice,long user,NotificationPreferences.Preferences p) {
        if(p.push()) for(long id:jdbc.query("SELECT ID_SUSCRIPCION FROM APP_PUSH_SUSCRIPCION WHERE ID_USUARIO=?",(rs,n)->rs.getLong(1),user))
            jdbc.update("INSERT INTO APP_NOTIF_ENVIO(ID_NOTIFICACION,CANAL,DESTINO) VALUES(?,'PUSH',?)",notice,String.valueOf(id));
        if(p.mail() && p.email()!=null && !p.email().isBlank()) jdbc.update("INSERT INTO APP_NOTIF_ENVIO(ID_NOTIFICACION,CANAL,DESTINO) VALUES(?,'EMAIL',?)",notice,p.email());
    }

    @Scheduled(
        fixedDelayString = "${marigex.delivery.delay:30000}",
        initialDelayString = "${marigex.delivery.initial-delay:60000}")
public void deliver() {
    jdbc.update(
            "UPDATE APP_NOTIF_ENVIO " +
            "SET ESTADO='PENDIENTE' " +
            "WHERE ESTADO='ENVIANDO' AND PROXIMO_INTENTO<?",
            Timestamp.from(Instant.now().minusSeconds(600)));

    int pushAvailable = transport.pushReady() ? 1 : 0;
    int emailAvailable = transport.mailReady() ? 1 : 0;

    if (pushAvailable == 0 && emailAvailable == 0) {
        return;
    }

    String sql = """
            SELECT
                D.ID_ENVIO,
                D.ID_NOTIFICACION,
                D.CANAL,
                D.DESTINO,
                D.INTENTOS,
                N.ID_USUARIO,
                N.MODULO,
                N.MENSAJE
            FROM APP_NOTIF_ENVIO D
            JOIN APP_NOTIFICACION N
                ON N.ID_NOTIFICACION = D.ID_NOTIFICACION
            WHERE D.ESTADO = 'PENDIENTE'
              AND D.PROXIMO_INTENTO <= SYSTIMESTAMP
              AND (
                    (D.CANAL = 'PUSH' AND ? = 1)
                 OR (D.CANAL = 'EMAIL' AND ? = 1)
              )
            ORDER BY D.ID_ENVIO
            FETCH FIRST 30 ROWS ONLY
            """;

    var rows = jdbc.query(
            sql,
            (rs, rowNum) -> new Delivery(
                    rs.getLong(1),
                    rs.getLong(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getInt(5),
                    rs.getLong(6),
                    rs.getString(7),
                    rs.getString(8)),
            pushAvailable,
            emailAvailable);

    for (var delivery : rows) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        int claimed = jdbc.update(
                "UPDATE APP_NOTIF_ENVIO " +
                "SET ESTADO='ENVIANDO', PROXIMO_INTENTO=SYSTIMESTAMP " +
                "WHERE ID_ENVIO=? AND ESTADO='PENDIENTE'",
                delivery.id());

        if (claimed != 1) {
            continue;
        }

        try {
            send(delivery);
        } catch (Exception ex) {
            int attempts = delivery.attempts() + 1;

            jdbc.update(
                    "UPDATE APP_NOTIF_ENVIO " +
                    "SET ESTADO=?, INTENTOS=?, PROXIMO_INTENTO=?, " +
                    "ULTIMO_ERROR=? WHERE ID_ENVIO=?",
                    attempts >= 5 ? "FALLIDO" : "PENDIENTE",
                    attempts,
                    Timestamp.from(
                            Instant.now().plusSeconds(
                                    60L * (1L << attempts))),
                    ex.getClass().getSimpleName(),
                    delivery.id());

            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
    private void send(Delivery d) throws Exception {
        var names=jdbc.query("SELECT E.NOMBRE FROM APP_USUARIO U JOIN APP_EMPLEADO E ON E.ID_EMPLEADO=U.ID_EMPLEADO WHERE U.ID_USUARIO=? AND U.ACTIVO='S'",(rs,n)->rs.getString(1),d.user());
        var p=preferences.get(d.user());
        if(names.isEmpty() || (d.channel().equals("PUSH")&&!p.push()) || (d.channel().equals("EMAIL")&&(!p.mail()||!d.target().equals(p.email())))) { finish(d,"OMITIDO");return; }
        if(d.channel().equals("PUSH")) {
            var subscriptions=jdbc.query("SELECT ENDPOINT,P256DH,AUTH FROM APP_PUSH_SUSCRIPCION WHERE ID_SUSCRIPCION=? AND ID_USUARIO=?",(rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3)},Long.parseLong(d.target()),d.user());
            if(subscriptions.isEmpty()) { finish(d,"OMITIDO");return; }
            var s=subscriptions.getFirst();
            String payload=json.writeValueAsString(Map.of("title","MariGex · "+d.module(),"body",pushPreview(d.message()),"userId",String.valueOf(d.user()),"tag","marigex-"+d.notice(),"url","/"));
            int code=transport.push(s[0],s[1],s[2],payload);
            if(code==404||code==410) {
                jdbc.update("DELETE FROM APP_PUSH_SUSCRIPCION WHERE ID_SUSCRIPCION=? AND ID_USUARIO=?",Long.parseLong(d.target()),d.user());finish(d,"OMITIDO");return;
            }
            if(code<200||code>=300) throw new IllegalStateException("Push delivery rejected");
        } else transport.mail(d.target(),names.getFirst(),d.module(),d.message());
        finish(d,"ENVIADO");
    }
    private static String pushPreview(String message) {
        // Keep encrypted payload below Web Push's 4096-byte minimum, including JSON escapes.
        int count=message.codePointCount(0,message.length());
        return count<=400?message:message.substring(0,message.offsetByCodePoints(0,400))+"…";
    }
    private void finish(Delivery d,String state) { jdbc.update("UPDATE APP_NOTIF_ENVIO SET ESTADO=?,ULTIMO_ERROR=NULL WHERE ID_ENVIO=?",state,d.id()); }
    private record Delivery(long id,long notice,String channel,String target,int attempts,long user,String module,String message) {}
}
