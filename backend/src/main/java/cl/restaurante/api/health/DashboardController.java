package cl.restaurante.api.health;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    public DashboardController(JdbcTemplate jdbc, RoleAccessService access) { this.jdbc = jdbc; this.access = access; }

    @GetMapping
    public Map<String, Long> metrics(HttpSession session) {
        RoleAccessService.User user = access.current(session);
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("Personal", count("SELECT COUNT(*) FROM APP_EMPLEADO"));
        m.put("Personal activo", count("SELECT COUNT(*) FROM APP_EMPLEADO WHERE UPPER(ESTADO) = 'ACTIVO'"));
        m.put("Personal inactivo", count("SELECT COUNT(*) FROM APP_EMPLEADO WHERE UPPER(ESTADO) <> 'ACTIVO'"));
        m.put("Asistencia", count("SELECT COUNT(DISTINCT EMPLEADO_ID) FROM APP_ASISTENCIA WHERE TRUNC(FECHA) = TRUNC(SYSDATE) AND UPPER(ESTADO) NOT IN ('AUSENTE','INASISTENTE','ANULADA','ANULADO')"));
        m.put("Horarios", count("SELECT COUNT(*) FROM APP_TURNO WHERE TRUNC(FECHA) = TRUNC(SYSDATE)"));
        m.put("Tareas diarias", count("SELECT COUNT(*) FROM APP_TAREA_DIARIA WHERE TRUNC(FECHA) = TRUNC(SYSDATE) AND UPPER(ESTADO) NOT IN ('COMPLETADA','COMPLETADO','FINALIZADA','FINALIZADO')"));
        m.put("Inventario", count("SELECT COUNT(*) FROM APP_INSUMO WHERE STOCK_ACTUAL < STOCK_MINIMO"));
        m.put("Cocina", count("SELECT COUNT(*) FROM APP_COCINA WHERE UPPER(ESTADO) NOT IN ('COMPLETADA','COMPLETADO','FINALIZADA','FINALIZADO')"));
        m.put("Producción", count("SELECT COUNT(*) FROM APP_PRODUCCION WHERE TRUNC(FECHA) = TRUNC(SYSDATE)"));
        m.put("Reservas", count("SELECT COUNT(*) FROM APP_RESERVA WHERE TRUNC(FECHA) = TRUNC(SYSDATE)"));
        m.put("Limpieza", count("SELECT COUNT(*) FROM APP_LIMPIEZA WHERE UPPER(ESTADO) NOT IN ('COMPLETADA','COMPLETADO','FINALIZADA','FINALIZADO')"));
        m.put("Comunicación del equipo", count("SELECT COUNT(*) FROM APP_COMUNICACION WHERE DESTINATARIO_USUARIO=? AND UPPER(ESTADO) NOT IN ('CERRADA','RESPONDIDA')", user.id()));
        m.put("Compras", count("SELECT COUNT(*) FROM APP_COMPRA WHERE UPPER(ESTADO) NOT IN ('RECIBIDA','RECIBIDO','COMPLETADA','COMPLETADO')"));
        m.put("Mermas", count("SELECT COUNT(*) FROM APP_MERMA WHERE FECHA >= TRUNC(SYSDATE, 'IW')"));
        m.put("Control sanitario", count("SELECT COUNT(*) FROM APP_CONTROL_SANITARIO WHERE UPPER(ESTADO) NOT IN ('COMPLETADO','COMPLETADA','OK','CUMPLE')"));
        m.put("Reportes", count("SELECT COUNT(*) FROM APP_REPORTE_DIARIO WHERE CREADO_EN >= SYSTIMESTAMP - INTERVAL '15' DAY"));
        return m;
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
}
