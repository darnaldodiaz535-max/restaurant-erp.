package cl.restaurante.api.health;

import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificationSettingsController {
    private final RoleAccessService access;
    private final NotificationPreferences preferences;
    private final PushSubscriptions subscriptions;
    private final NotificationTransport transport;
    public NotificationSettingsController(RoleAccessService access,NotificationPreferences preferences,PushSubscriptions subscriptions,NotificationTransport transport) {
        this.access=access;this.preferences=preferences;this.subscriptions=subscriptions;this.transport=transport;
    }
    @GetMapping("/preferencias")
    public NotificationPreferences.Preferences get(HttpSession session) { return preferences.get(access.current(session).id()); }
    @PutMapping("/preferencias")
    public NotificationPreferences.Preferences save(@RequestBody NotificationPreferences.Preferences p,HttpSession session) { return preferences.save(access.current(session).id(),p); }
    @GetMapping("/push/config")
    public Map<String,Object> config(HttpSession session) {
        access.current(session);
        return Map.of("configured",transport.pushReady(),"publicKey",transport.pushReady()?transport.publicKey():"","mailConfigured",transport.mailReady());
    }
    @PostMapping("/push/suscripciones")
    public Map<String,String> subscribe(@RequestBody PushSubscriptions.Subscription s,HttpSession session) {
        long user=access.current(session).id();
        if(!transport.pushReady()) throw new IllegalArgumentException("Push está pendiente de configuración en el servidor.");
        subscriptions.save(user,s);return Map.of("status","suscrito");
    }
    @DeleteMapping("/push/suscripciones")
    public Map<String,String> unsubscribe(@RequestBody Map<String,String> body,HttpSession session) {
        subscriptions.remove(access.current(session).id(),body.get("endpoint"));return Map.of("status","desactivado");
    }
}
