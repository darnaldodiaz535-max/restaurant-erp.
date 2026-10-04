package cl.restaurante.api.health;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Encoding;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Transport only. Recipients/preferences/retries are handled by the durable outbox. */
@Service
public class NotificationTransport {
    private final Environment env;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public NotificationTransport(Environment env) { this.env=env; if(Security.getProvider("BC")==null) Security.addProvider(new BouncyCastleProvider()); }
    public String publicKey() { return env.getProperty("MARIGEX_VAPID_PUBLIC_KEY",""); }
    public boolean pushReady() { return !publicKey().isBlank() && !env.getProperty("MARIGEX_VAPID_PRIVATE_KEY","").isBlank() && !env.getProperty("MARIGEX_VAPID_SUBJECT","").isBlank(); }
    public boolean mailReady() { return env.getProperty("MARIGEX_MAIL_ENABLED",Boolean.class,false) && !env.getProperty("MARIGEX_SMTP_HOST","").isBlank() && !env.getProperty("MARIGEX_MAIL_FROM","").isBlank(); }
    public int push(String endpoint,String key,String auth,String payload) throws Exception {
        PushSubscriptions.validateEndpoint(endpoint);
        var service=new PushService(publicKey(),env.getRequiredProperty("MARIGEX_VAPID_PRIVATE_KEY"),env.getRequiredProperty("MARIGEX_VAPID_SUBJECT"));
        // Library handles RFC encryption/VAPID. Native HTTP enforces bounded time and no redirects.
        var encrypted=service.preparePost(new Notification(endpoint,key,auth,payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),3600),Encoding.AES128GCM);
        var request=HttpRequest.newBuilder(encrypted.getURI()).timeout(Duration.ofSeconds(15));
        for(var header:encrypted.getAllHeaders()) request.header(header.getName(),header.getValue());
        byte[] bytes=encrypted.getEntity().getContent().readAllBytes();
        return http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    public void mail(String recipient,String name,String module,String message) {
        var sender=new JavaMailSenderImpl();
        sender.setHost(env.getRequiredProperty("MARIGEX_SMTP_HOST"));
        sender.setPort(env.getProperty("MARIGEX_SMTP_PORT",Integer.class,587));
        sender.setUsername(env.getProperty("MARIGEX_SMTP_USERNAME",""));
        sender.setPassword(env.getProperty("MARIGEX_SMTP_PASSWORD",""));
        sender.setDefaultEncoding("UTF-8");
        var props=sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth",String.valueOf(!sender.getUsername().isBlank()));
        props.setProperty("mail.smtp.starttls.enable",env.getProperty("MARIGEX_SMTP_STARTTLS","true"));
        props.setProperty("mail.smtp.starttls.required",env.getProperty("MARIGEX_SMTP_STARTTLS","true"));
        props.setProperty("mail.smtp.ssl.enable",env.getProperty("MARIGEX_SMTP_SSL","false"));
        props.setProperty("mail.smtp.ssl.checkserveridentity","true");
        for(String key:new String[]{"connectiontimeout","timeout","writetimeout"}) props.setProperty("mail.smtp."+key,"10000");
        var mail=new SimpleMailMessage();mail.setFrom(env.getRequiredProperty("MARIGEX_MAIL_FROM"));mail.setTo(recipient);
        mail.setSubject(module.replaceAll("[\\r\\n]", " ")+" – MariGex");
        mail.setText("Hola "+name+",\n\n"+message+"\n\nConsulta MariGex para ver los detalles.\n\nMariGex – Sistema de Gestión");
        sender.send(mail);
    }
}
