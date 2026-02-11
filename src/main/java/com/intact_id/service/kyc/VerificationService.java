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
import com.intact_id.util.TenantContext;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

    @Transactional
    public KycVerificationResponseDto submitVerification(
            String tier,
            KycVerificationRequestDto request,
            Company company,
            ApiKeyEnvironment environment) {

        log.info("Submitting {} verification for company: {}, tier: {}", environment.name(), company.getId(), tier);

        // Validate tier-specific requirements
        tierValidationService.validateTier(tier, request);

        // Create verification request
        VerificationRequest verificationRequest = new VerificationRequest();
        verificationRequest.setCompany(company);
        verificationRequest.setTier(tier);
        verificationRequest.setVerificationType(request.getVerificationType());
        verificationRequest.setStatus(GlobalStatuses.SUBMITTED);
        verificationRequest.setWebhookUrl(request.getWebhookUrl());
        verificationRequest.setEnvironment(environment);

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

        VerificationRequest savedRequest = verificationRequestRepository.save(verificationRequest);

        // Save documents immediately — MultipartFile objects are tied to the HTTP request
        // lifecycle and won't be available when the scheduler processes them later
        saveDocument(savedRequest, request.getSelfie(), "selfie");
        saveDocument(savedRequest, request.getIdFront(), "id_front");
        if (request.getIdBack() != null)
            saveDocument(savedRequest, request.getIdBack(), "id_back");
        if (request.getProofOfAddress() != null)
            saveDocument(savedRequest, request.getProofOfAddress(), "proof_of_address");

        return KycVerificationResponseDto.builder()
                .verificationId(savedRequest.getId())
                .status(savedRequest.getStatus().name())
                .tier(tier)
                .estimatedCompletion(LocalDateTime.now().plusMinutes(10))
                .webhookUrl(savedRequest.getWebhookUrl())
                .message("Verification submitted successfully and is being processed")
                .build();
    }

    private void addDocument(List<VerificationDocument> list, VerificationDocument doc) {
        if (doc != null)
            list.add(doc);
    }

    @Async
    @Transactional
    public void processVerificationAsync(VerificationRequest request, KycVerificationRequestDto requestDto) {
        try {
            log.info("Starting async verification processing for: {}", request.getId());

            // Update status to processing
            request.markAsProcessing();
            verificationRequestRepository.save(request);

            // Save documents in background (this was blocking the API before)
            List<VerificationDocument> documents = new java.util.ArrayList<>();
            addDocument(documents, saveDocument(request, requestDto.getSelfie(), "selfie"));
            addDocument(documents, saveDocument(request, requestDto.getIdFront(), "id_front"));
            if (requestDto.getIdBack() != null)
                addDocument(documents, saveDocument(request, requestDto.getIdBack(), "id_back"));
            if (requestDto.getProofOfAddress() != null)
                addDocument(documents, saveDocument(request, requestDto.getProofOfAddress(), "proof_of_address"));

            // Call external KYC service (mocked)
            ExternalKycServiceClient.VerificationResult result = externalKycServiceClient
                    .performVerification(request.getTier(), request, documents).get();

            // Save verification steps
            for (ExternalKycServiceClient.CheckResult check : result.getChecks()) {
                VerificationStep step = new VerificationStep();
                step.setVerificationRequest(request);
                step.setStepName(check.getStepName());
                step.setStatus("passed".equals(check.getStatus()) ? GlobalStatuses.COMPLETED : GlobalStatuses.FAILED);
                step.setConfidenceScore(check.getConfidenceScore());
                step.setResult(check.getResult());
                step.setExecutedAt(LocalDateTime.now());
                verificationStepRepository.save(step);
            }

            // Update verification request with final result
            request.markAsCompleted(result.getOverallDecision(), result.getRiskScore());

            // Store error details if present
            if (result.getFailureReason() != null) {
                request.setFailureReason(result.getFailureReason());
            }
            if (result.getErrorDetails() != null) {
                request.setErrorDetails(result.getErrorDetails());
            }

            verificationRequestRepository.save(request);

            log.info("Verification completed successfully for: {}", request.getId());

            // Send webhook notification
            webhookService.sendVerificationCompletedWebhook(request.getCompany(), request);

        } catch (Exception e) {
            log.error("Verification processing failed for: {}", request.getId(), e);

            String failureReason = "SystemError";
            String errorDetails = e.getMessage();

            // Extract more specific error information if available
            if (e.getCause() != null && e.getCause() instanceof feign.FeignException) {
                feign.FeignException feignException = (feign.FeignException) e.getCause();
                try {
                    String responseBody = feignException.contentUTF8();
                    if (responseBody != null && !responseBody.isEmpty()) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode errorJson = mapper.readTree(responseBody);

                        if (errorJson.has("error")) {
                            failureReason = errorJson.get("error").asText();
                        }
                        if (errorJson.has("message")) {
                            errorDetails = errorJson.get("message").asText();
                        }
                    }
                } catch (Exception parseEx) {
                    log.warn("Failed to parse error response", parseEx);
                }
            }

            request.markAsFailed(failureReason, errorDetails);
            verificationRequestRepository.save(request);

            // Send webhook notification for failure
            webhookService.sendVerificationFailedWebhook(request.getCompany(), request);
        }
    }

    private VerificationDocument saveDocument(VerificationRequest request, MultipartFile file, String documentType) {
        if (file == null || file.isEmpty())
            return null;

        try {
            // Create uploads directory if not exists
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(System.getProperty("user.dir"), "uploads");
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String filename = request.getId() + "_" + documentType + "_" + file.getOriginalFilename();
            java.nio.file.Path filePath = uploadPath.resolve(filename);

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

        List<VerificationStep> steps = verificationStepRepository
                .findByVerificationRequestIdOrderByCreatedAtAsc(verificationId);

        List<VerificationDocument> documents = verificationDocumentRepository
                .findByVerificationRequestId(verificationId);

        return VerificationStatusResponseDto.builder()
                .verificationId(request.getId())
                .companyName(request.getCompany() != null ? request.getCompany().getLegalName() : null)
                .status(request.getStatus().name())
                .tier(request.getTier())
                .verificationType(request.getVerificationType())
                .overallDecision(request.getOverallDecision())
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
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {

        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        Page<VerificationRequest> requests = verificationRequestRepository.findByFilters(
                resolvedCompanyId, environment, status, tier, fromDate, toDate, pageable);

        return requests.map(request -> VerificationStatusResponseDto.builder()
                .verificationId(request.getId())
                .companyName(request.getCompany() != null ? request.getCompany().getLegalName() : null)
                .status(request.getStatus().name())
                .tier(request.getTier())
                .verificationType(request.getVerificationType())
                .overallDecision(request.getOverallDecision())
                .riskScore(request.getRiskScore())
                .failureReason(request.getFailureReason())
                .customerData(request.getCustomerData())
                .createdAt(request.getCreatedAt())
                .completedAt(request.getCompletedAt())
                .build());
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
                .build();
    }
}
