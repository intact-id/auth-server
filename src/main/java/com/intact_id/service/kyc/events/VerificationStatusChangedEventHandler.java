package com.intact_id.service.kyc.events;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyCommunicationDetails;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.CompanyCommunicationRepository;
import com.intact_id.db.repository.kyc.VerificationRequestRepository;
import com.intact_id.service.email.EmailService;
import com.intact_id.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationStatusChangedEventHandler {

    private final WebhookService webhookService;
    private final EmailService emailService;
    private final VerificationRequestRepository verificationRequestRepository;
    private final CompanyCommunicationRepository companyCommunicationRepository;

    /**
     * Fires after the scheduler's transaction commits, so the handler always
     * reads the final persisted status from the DB.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(VerificationStatusChangedEvent event) {
        VerificationStatusChangedData data = event.getData();

        VerificationRequest request = verificationRequestRepository
                .findByIdWithCompany(data.getVerificationId())
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

        sendApplicantEmailIfConfigured(request, status);
    }

    private void sendApplicantEmailIfConfigured(VerificationRequest request, GlobalStatuses status) {
        Map<String, Object> customerData = request.getCustomerData();
        if (customerData == null) return;

        String applicantEmail = (String) customerData.get("email");
        if (applicantEmail == null || applicantEmail.isBlank()) return;

        String companyId = request.getCompany() != null ? request.getCompany().getId() : null;
        if (companyId == null) return;

        CompanyCommunicationDetails comms = companyCommunicationRepository
                .findByCompanyId(companyId).orElse(null);

        String companyName = request.getCompany().getLegalName();
        String firstName = (String) customerData.getOrDefault("firstName", "");
        String lastName = (String) customerData.getOrDefault("lastName", "");
        String applicantName = (firstName + " " + lastName).trim();
        if (applicantName.isEmpty()) applicantName = applicantEmail;

        if (status == GlobalStatuses.COMPLETED) {
            if (comms != null && !comms.shouldNotifyApplicantOnApproval()) return;
            emailService.sendApplicantResultEmail(
                    applicantEmail, applicantName, companyName,
                    "approved", null, request.getId());

        } else if (status == GlobalStatuses.REJECTED) {
            if (comms != null && !comms.shouldNotifyApplicantOnRejection()) return;
            emailService.sendApplicantResultEmail(
                    applicantEmail, applicantName, companyName,
                    "rejected", request.getErrorDetails(), request.getId());

        } else if (status == GlobalStatuses.PENDING_APPROVAL) {
            if (comms == null || !comms.shouldNotifyApplicantOnPendingReview()) return;
            emailService.sendApplicantResultEmail(
                    applicantEmail, applicantName, companyName,
                    "pending_review", null, request.getId());
        }
    }
}
