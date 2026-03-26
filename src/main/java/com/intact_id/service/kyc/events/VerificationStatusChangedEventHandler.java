package com.intact_id.service.kyc.events;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.kyc.VerificationRequestRepository;
import com.intact_id.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationStatusChangedEventHandler {

    private final WebhookService webhookService;
    private final VerificationRequestRepository verificationRequestRepository;

    /**
     * Fires after the scheduler's transaction commits, so the handler always
     * reads the final persisted status from the DB.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VerificationStatusChangedEvent event) {
        VerificationStatusChangedData data = event.getData();

        VerificationRequest request = verificationRequestRepository
                .findById(data.getVerificationId())
                .orElse(null);

        if (request == null) {
            log.warn("VerificationStatusChangedEvent: request not found: {}", data.getVerificationId());
            return;
        }

        GlobalStatuses status = data.getStatus();
        log.info("Handling status change event: {} → {}", data.getVerificationId(), status);

        if (status == GlobalStatuses.COMPLETED || status == GlobalStatuses.REJECTED) {
            webhookService.sendVerificationCompletedWebhook(request.getCompany(), request);
        } else if (status == GlobalStatuses.FAILED) {
            webhookService.sendVerificationFailedWebhook(request.getCompany(), request);
        } else {
            log.debug("No webhook action for status: {}", status);
        }
    }
}
