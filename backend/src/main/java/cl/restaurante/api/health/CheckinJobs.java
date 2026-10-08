package cl.restaurante.api.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "marigex.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class CheckinJobs {
    private static final Logger log = LoggerFactory.getLogger(CheckinJobs.class);
    private final CheckinService checkin;

    public CheckinJobs(CheckinService checkin) {
        this.checkin = checkin;
    }

    /** Reconciles current and completed weeks even after a Render restart or sleep. */
    @Scheduled(
            fixedDelayString = "${marigex.checkin.reconcile-delay:60000}",
            initialDelayString = "${marigex.checkin.initial-delay:45000}")
    public void reconcile() {
        try {
            checkin.reconcile();
        } catch (RuntimeException error) {
            log.warn("No se pudo reconciliar Check-in Diario; se reintentará.", error);
        }
    }
}
