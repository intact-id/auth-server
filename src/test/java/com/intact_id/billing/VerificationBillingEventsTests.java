package com.intact_id.billing;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.VerificationOutcome;
import com.intact_id.orchestration.VerificationSubmitted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Needs a database (like IntactIdApplicationTests): orchestration's events reach billing after commit. */
@SpringBootTest
class VerificationBillingEventsTests {

    @MockitoBean
    BillingService billingService;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void outcomeIsBilledAfterCommitAndTheStoredEventIsCleared() {
        VerificationOutcome outcome = new VerificationOutcome("ver-test-1", "company-1", "CLIENT-1", "tier1",
                GlobalStatuses.COMPLETED, "approved", null, null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(outcome));

        verify(billingService, timeout(5000)).handleVerificationOutcome(outcome);
        await().untilAsserted(() -> assertThat(
                jdbc.queryForObject("SELECT count(*) FROM event_publication", Integer.class)).isZero());
    }

    @Test
    void rolledBackSubmissionIsNeverBilled() throws InterruptedException {
        VerificationSubmitted submitted = new VerificationSubmitted("ver-test-2", "company-1", "tier1");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(submitted);
            status.setRollbackOnly();
        });

        Thread.sleep(1000);
        verify(billingService, never()).handleVerificationSubmitted(submitted);
    }
}
