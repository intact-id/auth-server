package com.intact_id.orchestration.verification;

import com.intact_id.accounts.Company;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.events.VerificationWebhooks;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualReviewScopeTest {

    private final VerificationRequestRepository repository = mock(VerificationRequestRepository.class);
    private final ManualReviewService service = new ManualReviewService(repository, mock(VerificationService.class),
            mock(VerificationWebhooks.class), mock(ApplicationEventPublisher.class));

    @Test
    void aCompanyCannotResolveAnotherCompanysReview() {
        when(repository.findById("v-1")).thenReturn(Optional.of(pendingFor("company-b")));

        assertThatThrownBy(() -> service.resolveReview("company-a", "v-1", "approved", null, "admin@a.com"))
                .isInstanceOf(ApplicationExceptions.IntactCompanyNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void ownCompanyAndSuperAdminsCanResolve() {
        VerificationRequest request = pendingFor("company-a");
        when(repository.findById("v-1")).thenReturn(Optional.of(request));

        service.resolveReview("company-a", "v-1", "approved", null, "admin@a.com");
        assertThat(request.getStatus()).isNotEqualTo(GlobalStatuses.PENDING_APPROVAL);

        VerificationRequest other = pendingFor("company-b");
        when(repository.findById("v-2")).thenReturn(Optional.of(other));
        service.resolveReview(null, "v-2", "rejected", null, "super@intact.com");
        assertThat(other.getStatus()).isNotEqualTo(GlobalStatuses.PENDING_APPROVAL);
    }

    private static VerificationRequest pendingFor(String companyId) {
        Company company = new Company();
        company.setId(companyId);
        VerificationRequest request = new VerificationRequest();
        request.setCompany(company);
        request.setStatus(GlobalStatuses.PENDING_APPROVAL);
        return request;
    }
}
