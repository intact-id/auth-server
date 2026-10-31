package com.intact_id.service.kyc;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.model.kyc.VerificationStep;
import com.intact_id.db.repository.kyc.VerificationDocumentRepository;
import com.intact_id.db.repository.kyc.VerificationRequestRepository;
import com.intact_id.db.repository.kyc.VerificationStepRepository;
import com.intact_id.dto.request.KycVerificationRequestDto;
import com.intact_id.dto.response.KycVerificationResponseDto;
import com.intact_id.dto.response.VerificationStatusResponseDto;
import com.intact_id.service.billing.BillingService;
import com.intact_id.util.TenantContext;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final VerificationStepRepository verificationStepRepository;
    private final TierValidationService tierValidationService;
    private final ExternalKycServiceClient externalKycServiceClient;
    private final com.intact_id.service.webhook.WebhookService webhookService;
    private final BillingService billingService;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.documents.base-dir:${user.dir}/uploads}")
    private String documentsBaseDir;

    /** Document type prefix for active-liveness frames: liveness_{index}_{label}. */
    public static final String LIVENESS_DOC_PREFIX = "liveness_";
    private static final List<String> LIVENESS_LABELS = List.of("neutral", "turn_left", "turn_right");

    /**
     * Deliberately NOT @Transactional at the method level. The old version was, which meant
     * the DB connection stayed checked out for the entire method — including the synchronous
     * multi-file disk writes below. Under concurrent load that starved the (small, default)
     * HikariCP pool: every request held a connection through several MB of disk I/O, so most
     * of a 100-concurrent-request burst just queued for a free connection instead of being
     * rejected fast or processed fast. Only the DB-only part (build + save the request, run
     * the billing hook) needs an actual transaction boundary — scoped explicitly below via
     * TransactionTemplate so the connection is released before any file I/O starts. Each
     * saveDocument() call still gets its own short, implicit transaction from Spring Data's
     * repository.save() — never held across another document's I/O.
     */
    public KycVerificationResponseDto submitVerification(
            String tier,
            KycVerificationRequestDto request,
            Company company,
            ApiKeyEnvironment environment) {

        log.info("Submitting {} verification for company: {}, tier: {}", environment.name(), company.getId(), tier);

        // Validate tier-specific requirements — in-memory, no DB/IO involved
        tierValidationService.validateTier(tier, request);
        List<String> livenessLabels = parseLivenessLabels(request);
        String externalReference = normalizeExternalReference(request.getExternalReference());

        VerificationRequest savedRequest = new TransactionTemplate(transactionManager).execute(status -> {
            // Create verification request
            VerificationRequest verificationRequest = new VerificationRequest();
            verificationRequest.setCompany(company);
            verificationRequest.setTier(tier);
            verificationRequest.setVerificationType(request.getVerificationType());
            verificationRequest.setStatus(GlobalStatuses.SUBMITTED);
            verificationRequest.setWebhookUrl(request.getWebhookUrl());
            verificationRequest.setEnvironment(environment);
            verificationRequest.setExternalReference(externalReference);

            // Store customer data
            Map<String, Object> customerData = new HashMap<>();
            customerData.put("firstName", request.getFirstName());
            customerData.put("lastName", request.getLastName());
            customerData.put("dateOfBirth", request.getDateOfBirth());
            customerData.put("idNumber", request.getIdNumber());
            if (request.getIdType() != null)
                customerData.put("idType", request.getIdType());
            if (request.getNationality() != null)
                customerData.put("nationality", request.getNationality());
            if (request.getCountryOfResidence() != null)
                customerData.put("countryOfResidence", request.getCountryOfResidence());
            if (request.getPhone() != null)
                customerData.put("phone", request.getPhone());
            if (request.getEmail() != null)
                customerData.put("email", request.getEmail());

            verificationRequest.setCustomerData(customerData);
            verificationRequest
                    .setMetadata(request.getCustomFields() != null ? request.getCustomFields() : new HashMap<>());

            VerificationRequest saved = verificationRequestRepository.save(verificationRequest);
            billingService.handleVerificationSubmitted(saved);
            return saved;
        });

        // Save documents AFTER the transaction above has committed and released its
        // connection — MultipartFile objects are tied to the HTTP request lifecycle and
        // won't be available when the scheduler processes them later, so they still have to
        // happen here, just no longer while pinning a DB connection.
        saveDocument(savedRequest, request.getSelfie(), "selfie");
        saveDocument(savedRequest, request.getIdFront(), "id_front");
        if (request.getIdBack() != null)
            saveDocument(savedRequest, request.getIdBack(), "id_back");
        if (request.getProofOfAddress() != null)
            saveDocument(savedRequest, request.getProofOfAddress(), "proof_of_address");
        // Index keeps capture order; label tells the face service which challenge the frame answers
        for (int i = 0; i < livenessLabels.size(); i++)
            saveDocument(savedRequest, request.getLivenessFrames().get(i),
                    LIVENESS_DOC_PREFIX + i + "_" + livenessLabels.get(i));

        return KycVerificationResponseDto.builder()
                .verificationId(savedRequest.getId())
                .externalReference(savedRequest.getExternalReference())
                .status(savedRequest.getStatus().name())
                .tier(tier)
                .estimatedCompletion(LocalDateTime.now().plusMinutes(10))
                .webhookUrl(savedRequest.getWebhookUrl())
                .message("Verification submitted successfully and is being processed")
                .build();
    }

    /**
     * Validates the optional active-liveness frames before anything is persisted. Returns the
     * labels (one per frame), or an empty list when the submission has no liveness frames — e.g.
     * API customers that upload a single selfie, who stay on the single-photo liveness check.
     */
    private List<String> parseLivenessLabels(KycVerificationRequestDto request) {
        List<MultipartFile> frames = request.getLivenessFrames() == null ? List.of()
                : request.getLivenessFrames().stream().filter(f -> f != null && !f.isEmpty()).toList();
        String rawLabels = request.getLivenessLabels();
        if (frames.isEmpty() && (rawLabels == null || rawLabels.isBlank()))
            return List.of();

        List<String> labels = rawLabels == null ? List.of()
                : java.util.Arrays.stream(rawLabels.split(","))
                        .map(l -> l.trim().toLowerCase(Locale.ROOT))
                        .filter(l -> !l.isEmpty())
                        .toList();
        if (labels.size() != frames.size())
            throw new ApplicationExceptions.IntactBadRequestException(
                    "livenessLabels must have exactly one label per liveness frame");
        if (frames.size() < 3 || frames.size() > 8)
            throw new ApplicationExceptions.IntactBadRequestException("Between 3 and 8 liveness frames are required");
        if (!LIVENESS_LABELS.containsAll(labels) || !labels.containsAll(LIVENESS_LABELS))
            throw new ApplicationExceptions.IntactBadRequestException(
                    "livenessLabels must only use, and include each of: " + String.join(", ", LIVENESS_LABELS));

        request.setLivenessFrames(frames);
        return labels;
    }

    private VerificationDocument saveDocument(VerificationRequest request, MultipartFile file, String documentType) {
        if (file == null || file.isEmpty())
            return null;

        try {
            // Persist all documents under a configurable base directory.
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(documentsBaseDir).toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String filename = request.getId() + "_" + documentType + "_" + file.getOriginalFilename();
            java.nio.file.Path filePath = uploadPath.resolve(filename).normalize();

            // Save file
            file.transferTo(filePath.toFile());

            VerificationDocument document = new VerificationDocument();
            document.setVerificationRequest(request);
            document.setDocumentType(documentType);
            document.setFilePath(filePath.toString());
            document.setFileSize(file.getSize());
            document.setMimeType(file.getContentType());
            document.setOriginalFilename(file.getOriginalFilename());

            return verificationDocumentRepository.save(document);
        } catch (java.io.IOException e) {
            log.error("Failed to save document: {}", documentType, e);
            throw new ApplicationExceptions.IntactBadRequestException("Failed to save document");
        }
    }

    public VerificationStatusResponseDto getVerificationStatus(
            String verificationId,
            String companyId,
            ApiKeyEnvironment environment) {
        VerificationRequest request;
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);

        if (resolvedCompanyId != null) {
            // Company-specific access (API key authentication)
            request = verificationRequestRepository
                    .findByIdAndCompanyIdAndEnvironment(verificationId, resolvedCompanyId, environment)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Verification not found: " + verificationId));
        } else {
            // Admin access (JWT authentication) - can view any verification
            request = verificationRequestRepository
                    .findByIdAndEnvironment(verificationId, environment)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Verification not found: " + verificationId));
        }

        return toDetailedStatusDto(request);
    }

    public VerificationStatusResponseDto getLatestByExternalReference(
            String externalReference,
            String companyId,
            ApiKeyEnvironment environment) {
        String normalizedReference = normalizeExternalReference(externalReference);
        if (normalizedReference == null) {
            throw new ApplicationExceptions.IntactBadRequestException("externalReference is required");
        }
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        if (resolvedCompanyId == null) {
            throw new ApplicationExceptions.IntactBadRequestException("companyId is required");
        }

        VerificationRequest request = verificationRequestRepository
                .findFirstByCompanyIdAndEnvironmentAndExternalReferenceOrderByCreatedAtDesc(
                        resolvedCompanyId, environment, normalizedReference)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "No verification found for externalReference: " + normalizedReference));

        return toDetailedStatusDto(request);
    }

    private VerificationStatusResponseDto toDetailedStatusDto(VerificationRequest request) {
        List<VerificationStep> steps = verificationStepRepository
                .findByVerificationRequestIdOrderByCreatedAtAsc(request.getId());

        List<VerificationDocument> documents = verificationDocumentRepository
                .findByVerificationRequestId(request.getId());

        return VerificationStatusResponseDto.builder()
                .verificationId(request.getId())
                .externalReference(request.getExternalReference())
                .companyName(request.getCompany() != null ? request.getCompany().getLegalName() : null)
                .status(request.getStatus().name())
                .tier(request.getTier())
                .verificationType(request.getVerificationType())
                .overallDecision(request.getOverallDecision())
                .failureReason(request.getFailureReason())
                .errorDetails(request.getErrorDetails())
                .riskScore(request.getRiskScore())
                .createdAt(request.getCreatedAt())
                .completedAt(request.getCompletedAt())
                .customerData(request.getCustomerData())
                .steps(steps.stream().map(this::mapStepToDto).collect(Collectors.toList()))
                .documents(documents.stream().map(this::mapDocumentToDto).collect(Collectors.toList()))
                .build();
    }

    public Page<VerificationStatusResponseDto> listVerifications(
            String companyId,
            ApiKeyEnvironment environment,
            GlobalStatuses status,
            String tier,
            String externalReference,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String search,
            Pageable pageable) {

        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String normalizedReference = normalizeExternalReference(externalReference);
        String environmentName = environment != null ? environment.name() : null;
        String statusName = status != null ? status.name() : null;
        Page<VerificationRequest> requests = verificationRequestRepository.findByFilters(
                resolvedCompanyId, environmentName, statusName, tier, normalizedReference,
                fromDate, toDate, normalizedSearch, pageable);

        return requests.map(request -> VerificationStatusResponseDto.builder()
                .verificationId(request.getId())
                .externalReference(request.getExternalReference())
                .companyName(request.getCompany() != null ? request.getCompany().getLegalName() : null)
                .status(request.getStatus().name())
                .tier(request.getTier())
                .verificationType(request.getVerificationType())
                .overallDecision(request.getOverallDecision())
                .riskScore(request.getRiskScore())
                .failureReason(request.getFailureReason())
                .errorDetails(request.getErrorDetails())
                .customerData(request.getCustomerData())
                .createdAt(request.getCreatedAt())
                .completedAt(request.getCompletedAt())
                .build());
    }

    @Transactional
    public void releaseHeldVerification(String verificationId) {
        VerificationRequest request = verificationRequestRepository.findByIdWithCompany(verificationId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "Verification not found: " + verificationId));
        if (request.getStatus() != GlobalStatuses.ON_HOLD) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Verification " + verificationId + " is not ON_HOLD (current status: " + request.getStatus() + ")");
        }
        request.markAsSubmitted();
        verificationRequestRepository.save(request);
        log.info("Verification {} manually released back to SUBMITTED", verificationId);
    }

    @Transactional
    public int releaseAllHeldForCompany(String companyId) {
        List<VerificationRequest> held = companyId != null
                ? verificationRequestRepository.findByStatusAndCompanyId(GlobalStatuses.ON_HOLD, companyId)
                : verificationRequestRepository.findByStatus(GlobalStatuses.ON_HOLD);
        held.forEach(r -> r.markAsSubmitted());
        verificationRequestRepository.saveAll(held);
        log.info("Released {} held verification(s) for companyId={}", held.size(), companyId != null ? companyId : "ALL");
        return held.size();
    }

    private String normalizeExternalReference(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return null;
        }
        String trimmed = externalReference.trim();
        if (trimmed.length() > 255) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    "externalReference must be at most 255 characters");
        }
        return trimmed;
    }

    private VerificationStatusResponseDto.VerificationStepDto mapStepToDto(VerificationStep step) {
        return VerificationStatusResponseDto.VerificationStepDto.builder()
                .stepName(step.getStepName())
                .status(step.getStatus().name())
                .confidenceScore(step.getConfidenceScore())
                .result(step.getResult())
                .executedAt(step.getExecutedAt())
                .errorMessage(step.getErrorMessage())
                .build();
    }

    private VerificationStatusResponseDto.DocumentDto mapDocumentToDto(VerificationDocument document) {
        return VerificationStatusResponseDto.DocumentDto.builder()
                .id(document.getId())
                .documentType(document.getDocumentType())
                .originalFilename(document.getOriginalFilename())
                .fileSize(document.getFileSize())
                .mimeType(document.getMimeType())
                .uploadedAt(document.getCreatedAt())
                .extractedData(document.getExtractedData())
                .build();
    }

    private boolean shouldRejectVerification(String failureReason, String errorDetails, String overallDecision) {
        if ("rejected".equalsIgnoreCase(overallDecision)) {
            return true;
        }

        String failure = failureReason == null ? "" : failureReason.toLowerCase(Locale.ROOT);
        String details = errorDetails == null ? "" : errorDetails.toLowerCase(Locale.ROOT);

        return failure.contains("face not detected")
                || failure.contains("facenotdetected")
                || details.contains("face not detected")
                || details.contains("no face detected")
                || details.contains("no face found");
    }
}
