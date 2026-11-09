package com.intact_id.workflow;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.checks.FaceScreeningClient;
import com.intact_id.checks.OcrExtractionResponse;
import com.intact_id.checks.OcrScreeningClient;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.BalanceCheck;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.verification.VerificationProcessor;
import com.intact_id.orchestration.verification.VerificationService;
import com.intact_id.orchestration.web.KycVerificationRequestDto;
import com.intact_id.workflow.definition.WorkflowDefinitionRepository;
import com.intact_id.workflow.definition.WorkflowDefinitionService;
import com.intact_id.workflow.web.WorkflowDtos.CreateWorkflowRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Needs a database. A verification pinned to "ID check only" runs only OCR and follows the published graph. */
@SpringBootTest
class WorkflowRunIntegrationTests {

    @MockitoBean OcrScreeningClient ocr;
    @MockitoBean FaceScreeningClient face;
    @MockitoBean WorkflowPricing pricing;
    @MockitoBean BalanceCheck balanceCheck;

    @Autowired DataSource dataSource;
    @Autowired CompanyRepository companies;
    @Autowired WorkflowDefinitionRepository definitions;
    @Autowired WorkflowDefinitionService workflows;
    @Autowired VerificationService verificationService;
    @Autowired VerificationProcessor processor;
    @Autowired VerificationRequestRepository requests;

    Company company;

    @BeforeEach
    void setUp() {
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migrations/2026-11-09_add_workflow_service_catalog.sql"),
                new ClassPathResource("db/migrations/2026-11-09_add_workflow_definitions.sql")).execute(dataSource);
        company = new Company();
        company.setCompanyIdentifierCode("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        company.setLegalName("Workflow Test Ltd");
        company.setStatus(GlobalStatuses.ACTIVE);
        company = companies.save(company);

        when(pricing.price(anyString(), anyString(), any())).thenReturn(
                new WorkflowPricing.PriceQuote(new BigDecimal("40"), "KES", List.of(), List.of()));
        when(balanceCheck.check(anyString(), anyString(), any())).thenReturn(BalanceCheck.HoldDecision.ALLOW);

        String template = definitions.findByCompanyIdIsNullAndArchivedFalseOrderByNameAsc().stream()
                .filter(t -> t.getKey().equals("id-check")).findFirst().orElseThrow().getId();
        String id = workflows.create(company.getId(),
                new CreateWorkflowRequest("id-check", "ID check only", null, null, null, template, false), false).workflow().id();
        workflows.publish(company.getId(), id, null, "test");
    }

    @Test
    void approvedRunFollowsTheGraphAndNeverCallsTheFaceService() {
        when(ocr.extractDocument(anyString(), anyString(), any())).thenReturn(ocrResult("2031-01-01"));

        VerificationRequest done = submitAndProcess();

        assertThat(done.getStatus()).isEqualTo(GlobalStatuses.COMPLETED);
        assertThat(done.getOverallDecision()).isEqualTo("approved");
        assertThat(done.getWorkflowVersion()).isEqualTo(1);
        assertThat(done.getWorkflowPath()).extracting(WorkflowRun.Step::nodeId).containsExactly("start", "doc", "approved");
        verifyNoInteractions(face);
        verify(balanceCheck).check(company.getCompanyIdentifierCode(), done.getId(), done.getWorkflowId());
    }

    @Test
    void expiredDocumentTakesTheFailedPath() {
        when(ocr.extractDocument(anyString(), anyString(), any())).thenReturn(ocrResult("2020-01-01"));

        VerificationRequest done = submitAndProcess();

        assertThat(done.getOverallDecision()).isEqualTo("rejected");
        assertThat(done.getFailureReason()).isEqualTo("Document expired");
        assertThat(done.getWorkflowPath()).extracting(WorkflowRun.Step::port).containsExactly("next", "failed", null);
        verify(face, never()).verifyFace(anyString(), anyString(), any(), any());
    }

    private VerificationRequest submitAndProcess() {
        KycVerificationRequestDto dto = new KycVerificationRequestDto();
        dto.setFirstName("Jane");
        dto.setLastName("Wanjiru");
        dto.setDateOfBirth(LocalDate.of(1990, 5, 1));
        dto.setIdNumber("12345678");
        dto.setIdType("national_id");
        dto.setWorkflow("id-check");
        dto.setIdFront(new MockMultipartFile("idFront", "id.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3}));

        // No selfie: the workflow has no face or liveness step
        String id = verificationService.submitVerification("workflow", dto, company, ApiKeyEnvironment.DEV).getVerificationId();
        processor.process(requests.findByIdWithCompany(id).orElseThrow());
        return requests.findByIdWithCompany(id).orElseThrow();
    }

    private static OcrExtractionResponse ocrResult(String expiry) {
        OcrExtractionResponse response = new OcrExtractionResponse();
        response.setDocumentType("national_id");
        response.setDocumentTypeConfidence(0.97);
        response.setCountryIso3("KEN");
        response.setOverallConfidence(0.95);
        OcrExtractionResponse.OcrFields fields = new OcrExtractionResponse.OcrFields();
        fields.setFullName("JANE WANJIRU");
        fields.setIdNumber("12345678");
        fields.setDateOfBirth("1990-05-01");
        fields.setExpiryDate(expiry);
        response.setFields(fields);
        return response;
    }
}
