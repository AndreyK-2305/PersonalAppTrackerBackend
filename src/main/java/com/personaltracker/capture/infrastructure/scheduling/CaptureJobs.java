package com.personaltracker.capture.infrastructure.scheduling;

import com.personaltracker.capture.application.usecase.ProcessPendingNotifications;
import com.personaltracker.capture.application.usecase.PurgeRawNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tareas programadas de la captura. Con más de una instancia, el bloqueo {@code SKIP LOCKED} evita
 * procesar dos veces la misma notificación. Se desactivan con {@code app.capture.jobs.enabled=false}
 * (las pruebas las ejecutan a mano).
 */
@Component
@ConditionalOnProperty(name = "app.capture.jobs.enabled", havingValue = "true", matchIfMissing = true)
class CaptureJobs {

    private static final Logger log = LoggerFactory.getLogger(CaptureJobs.class);

    private final ProcessPendingNotifications processPending;
    private final PurgeRawNotifications purgeRaw;

    CaptureJobs(ProcessPendingNotifications processPending, PurgeRawNotifications purgeRaw) {
        this.processPending = processPending;
        this.purgeRaw = purgeRaw;
    }

    @Scheduled(fixedDelayString = "${app.capture.worker-interval:PT30S}", initialDelayString = "PT30S")
    void processQueue() {
        processPending.execute();
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Bogota")
    void purgeRawText() {
        int purged = purgeRaw.execute();
        if (purged > 0) {
            log.info("Se borró el texto de {} notificaciones con más de 45 días", purged);
        }
    }
}
