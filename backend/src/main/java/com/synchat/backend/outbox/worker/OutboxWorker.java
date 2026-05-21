package com.synchat.backend.outbox.worker;

import com.synchat.backend.common.config.AppProperties;
import com.synchat.backend.outbox.service.ClaimedOutboxEvent;
import com.synchat.backend.outbox.service.OutboxClaimService;
import com.synchat.backend.outbox.service.OutboxPublishService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxClaimService outboxClaimService;
    private final OutboxPublishService outboxPublishService;
    private final AppProperties appProperties;

    public OutboxWorker(
            OutboxClaimService outboxClaimService,
            OutboxPublishService outboxPublishService,
            AppProperties appProperties) {
        this.outboxClaimService = outboxClaimService;
        this.outboxPublishService = outboxPublishService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:100}")
    public void publishPendingOutboxEvents() {
        if (!appProperties.outbox().enabled()) {
            return;
        }

        try {
            List<ClaimedOutboxEvent> events = outboxClaimService.claimPending(appProperties.outbox().batchSize());
            for (ClaimedOutboxEvent event : events) {
                outboxPublishService.publish(event);
            }
        } catch (Exception exception) {
            log.warn("Unexpected failure while processing outbox events.", exception);
        }
    }
}
