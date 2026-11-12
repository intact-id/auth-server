package com.intact_id.orchestration;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.model.VerificationDocumentRepository;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Needs a database (like IntactIdApplicationTests). Runs the review queue, type filter and
 * document-count queries for real; each test rolls back.
 */
@SpringBootTest
@Transactional
class ReviewQueriesIntegrationTests {

    @Autowired CompanyRepository companyRepository;
    @Autowired VerificationRequestRepository verificationRequestRepository;
    @Autowired VerificationDocumentRepository verificationDocumentRepository;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setLegalName("Review Queries Test Ltd");
        company.setCompanyIdentifierCode("RQT-" + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(GlobalStatuses.ACTIVE);
        company = companyRepository.save(company);
    }

    @Test
    void reviewQueueIsOldestFirstAndFiltersByAssignee() {
        VerificationRequest newer = pendingReview(LocalDateTime.now().minusMinutes(5), "amina");
        VerificationRequest older = pendingReview(LocalDateTime.now().minusHours(2), null);
        VerificationRequest done = verification("individual");
        done.markAsCompleted("approved", 0.1);
        verificationRequestRepository.saveAndFlush(done);

        List<String> all = ids(verificationRequestRepository.findReviewQueue(
                GlobalStatuses.PENDING_APPROVAL, null, false, PageRequest.of(0, 50)).getContent());
        assertThat(all).containsSubsequence(older.getId(), newer.getId()).doesNotContain(done.getId());

        assertThat(ids(verificationRequestRepository.findReviewQueue(
                GlobalStatuses.PENDING_APPROVAL, "amina", false, PageRequest.of(0, 50)).getContent()))
                .contains(newer.getId()).doesNotContain(older.getId());

        assertThat(ids(verificationRequestRepository.findReviewQueue(
                GlobalStatuses.PENDING_APPROVAL, null, true, PageRequest.of(0, 50)).getContent()))
                .contains(older.getId()).doesNotContain(newer.getId());
    }

    @Test
    void listFiltersByVerificationTypeIgnoringCase() {
        VerificationRequest individual = verificationRequestRepository.saveAndFlush(verification("individual"));
        VerificationRequest business = verificationRequestRepository.saveAndFlush(verification("business"));

        List<String> found = ids(verificationRequestRepository.findByFilters(company.getId(), null, null, null,
                "BUSINESS", null, null, null, null, PageRequest.of(0, 50)).getContent());

        assertThat(found).containsExactly(business.getId()).doesNotContain(individual.getId());
    }

    @Test
    void documentCountsComeBackForAPageInOneQuery() {
        VerificationRequest twoDocs = verificationRequestRepository.saveAndFlush(verification("individual"));
        VerificationRequest noDocs = verificationRequestRepository.saveAndFlush(verification("individual"));
        document(twoDocs, "selfie");
        document(twoDocs, "id_front");

        Map<String, Long> counts = verificationDocumentRepository.countByVerificationIds(List.of(twoDocs.getId(), noDocs.getId()))
                .stream().collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));

        assertThat(counts).containsEntry(twoDocs.getId(), 2L).doesNotContainKey(noDocs.getId());
    }

    private VerificationRequest pendingReview(LocalDateTime requestedAt, String assignee) {
        VerificationRequest request = verification("individual");
        request.markAsManualReview(0.5, "Face match below threshold");
        request.setReviewRequestedAt(requestedAt);
        if (assignee != null) request.assignReviewer(assignee);
        return verificationRequestRepository.saveAndFlush(request);
    }

    private VerificationRequest verification(String type) {
        VerificationRequest request = new VerificationRequest();
        request.setCompany(company);
        request.setTier("tier1");
        request.setVerificationType(type);
        request.setEnvironment(ApiKeyEnvironment.PROD);
        return request;
    }

    private void document(VerificationRequest request, String type) {
        VerificationDocument document = new VerificationDocument();
        document.setVerificationRequest(request);
        document.setDocumentType(type);
        document.setFilePath("test/" + type);
        verificationDocumentRepository.saveAndFlush(document);
    }

    private static List<String> ids(List<VerificationRequest> requests) {
        return requests.stream().map(VerificationRequest::getId).toList();
    }
}
