package cl.restaurante.api.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "marigex.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DailyReportRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(DailyReportRetentionJob.class);
    private final JdbcTemplate jdbc;

    public DailyReportRetentionJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 10 3 * * *", zone = "America/Santiago")
    public void deleteExpiredReports() {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM APP_REPORTE_DIARIO "
                            + "WHERE CREADO_EN < SYSTIMESTAMP - INTERVAL '15' DAY");
            if (deleted > 0) log.info("Se eliminaron {} reportes diarios vencidos.", deleted);
        } catch (RuntimeException error) {
            log.warn("No se pudieron eliminar los reportes diarios vencidos; se reintentará mañana.", error);
        }
    }
}
