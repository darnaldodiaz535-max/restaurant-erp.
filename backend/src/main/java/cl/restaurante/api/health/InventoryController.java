package cl.restaurante.api.health;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inventario")
public class InventoryController {
    private final JdbcTemplate jdbc;
    private final RoleAccessService access;
    private final NotificationService notifications;

    public InventoryController(
            JdbcTemplate jdbc,
            RoleAccessService access,
            NotificationService notifications) {
        this.jdbc = jdbc;
        this.access = access;
        this.notifications = notifications;
    }

    @PostMapping("/enviar")
    @Transactional
    public Map<String, Object> send(
            @RequestBody SendInventoryRequest request,
            HttpSession session) {
        access.requireModuleEdit(session, "inventario", false);

        if (request == null || request.group() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona Cocina o Barra.");
        }

        String group = request.group().trim().toUpperCase(java.util.Locale.ROOT);
        if (!group.equals("COCINA") && !group.equals("BARRA")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El grupo debe ser Cocina o Barra.");
        }

        List<LowStockItem> items = jdbc.query(
                """
                SELECT NOMBRE, STOCK_ACTUAL, STOCK_MINIMO, UNIDAD
                FROM APP_INSUMO
                WHERE GRUPO = ?
                  AND STOCK_ACTUAL < STOCK_MINIMO
                ORDER BY NOMBRE, ID_INSUMO
                """,
                (rs, rowNum) -> new LowStockItem(
                        rs.getString("NOMBRE"),
                        rs.getBigDecimal("STOCK_ACTUAL"),
                        rs.getBigDecimal("STOCK_MINIMO"),
                        rs.getString("UNIDAD")),
                group);

        String areaLabel = group.equals("COCINA") ? "Cocina" : "Barra";

        if (items.isEmpty()) {
            return Map.of(
                    "sent", false,
                    "group", group,
                    "count", 0,
                    "message", "Inventario " + areaLabel
                            + " revisado correctamente. No hay productos por pedir.");
        }

        StringBuilder detail = new StringBuilder()
                .append("Inventario ")
                .append(areaLabel)
                .append(" — Productos por pedir\n\n");

        for (LowStockItem item : items) {
            detail.append("• ")
                    .append(item.name())
                    .append(" — Actual: ")
                    .append(number(item.current()))
                    .append(" ")
                    .append(item.unit())
                    .append(" / Mínimo: ")
                    .append(number(item.minimum()))
                    .append(" ")
                    .append(item.unit())
                    .append('\n');
        }

        String pushPreview = "Inventario " + areaLabel + ": "
                + items.size()
                + (items.size() == 1
                ? " producto necesita reposición."
                : " productos necesitan reposición.")
                + " Abre MariGex para ver el detalle.";

        notifications.administratorsWithDetail(
                "Inventario",
                pushPreview,
                detail.toString());

        return Map.of(
                "sent", true,
                "group", group,
                "count", items.size(),
                "message", "Inventario " + areaLabel
                        + " enviado. Productos por pedir: " + items.size() + ".");
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public record SendInventoryRequest(String group) {}

    private record LowStockItem(
            String name,
            BigDecimal current,
            BigDecimal minimum,
            String unit) {}
}