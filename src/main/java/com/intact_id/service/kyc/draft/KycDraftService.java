package com.intact_id.service.kyc.draft;

import com.intact_id.config.KycDraftConfig;
import com.intact_id.constant.enums.kyc.KycDraftStatus;
import com.intact_id.constant.enums.kyc.KycHandoffStatus;
import com.intact_id.constant.enums.kyc.WidgetChannel;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.kyc.KycDraft;
import com.intact_id.db.model.kyc.KycDraftDocument;
import com.intact_id.db.model.kyc.KycHandoff;
import com.intact_id.db.repository.kyc.KycDraftDocumentRepository;
import com.intact_id.db.repository.kyc.KycDraftRepository;
import com.intact_id.db.repository.kyc.KycHandoffRepository;
import com.intact_id.dto.request.KycVerificationRequestDto;
import com.intact_id.dto.request.kyc.DraftDetailsRequestDto;
import com.intact_id.dto.response.KycVerificationResponseDto;
import com.intact_id.dto.response.kyc.KycDraftResponseDto;
import com.intact_id.dto.response.kyc.KycDraftSessionResponseDto;
import com.intact_id.security.authentication.WidgetSessionContext;
import com.intact_id.service.kyc.KycSessionService;
import com.intact_id.service.kyc.VerificationService;
import com.intact_id.service.storage.DocumentStorage;
import com.intact_id.util.SecurityContextUtil;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycDraftService {

    private static final Set<String> DOCUMENT_TYPES = Set.of("selfie", "id_front", "id_back", "proof_of_address");
    // Same naming the one-shot submit uses: liveness_{index}_{label}
    private static final Pattern LIVENESS_TYPE = Pattern.compile("liveness_([0-7])_(neutral|turn_left|turn_right)");
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final int RESUME_TOKEN_BYTES = 32;

    private final KycDraftRepository draftRepository;
    private final KycDraftDocumentRepository documentRepository;
    private final KycHandoffRepository handoffRepository;
    private final DocumentStorage documentStorage;
    private final KycSessionService kycSessionService;
    private final VerificationService verificationService;
    private final GeneratorUtils generatorUtils;
    private final KycDraftConfig config;

    public KycDraftSessionResponseDto createDraft(WidgetSessionContext context) {
        requireEmbedded(context);
        String resumeToken = generatorUtils.generateUrlSafeToken(RESUME_TOKEN_BYTES);

        KycDraft draft = new KycDraft();
        draft.setCompanyId(context.companyId());
        draft.setEnvironment(context.environment());
        draft.setTier(context.tier());
        draft.setExternalReference(context.externalReference());
        draft.setResumeTokenHash(generatorUtils.hashString(resumeToken));
        draft.setExpiresAt(LocalDateTime.now().plus(config.getTtl()));
        draft = draftRepository.save(draft);

        log.info("Created KYC draft {} for company {}", draft.getId(), draft.getCompanyId());
        KycDraftSessionResponseDto response = sessionResponse(draft, WidgetChannel.EMBEDDED, null);
        response.setResumeToken(resumeToken);
        return response;
    }

    public KycDraftSessionResponseDto resumeDraft(WidgetSessionContext context, String resumeToken) {
        requireEmbedded(context);
        // Must match the current session exactly, so a shared browser can't pick up another user's draft
        KycDraft draft = draftRepository.findByResumeTokenHash(generatorUtils.hashString(resumeToken.trim()))
                .filter(d -> !d.isExpired())
                .filter(d -> d.getCompanyId().equals(context.companyId())
                        && d.getEnvironment() == context.environment()
                        && d.getTier().equalsIgnoreCase(context.tier())
                        && Objects.equals(d.getExternalReference(), context.externalReference()))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "No resumable draft found"));

        return sessionResponse(draft, WidgetChannel.EMBEDDED, null);
    }

    public KycDraftSessionResponseDto refreshToken(WidgetSessionContext context) {
        if (context.draftId() == null) {
            throw new ApplicationExceptions.IntactForbiddenException("Token is not bound to a draft");
        }
        KycDraft draft = requireEditableDraft(context, context.draftId());
        return sessionResponse(draft, context.channel(), context.handoffId());
    }

    public KycDraftResponseDto getDraft(WidgetSessionContext context, String draftId) {
        return toResponse(requireAccessibleDraft(context, draftId));
    }

    public KycDraftResponseDto updateDetails(WidgetSessionContext context, String draftId,
                                             DraftDetailsRequestDto request) {
        KycDraft draft = requireEditableDraft(context, draftId);

        Map<String, Object> details = new HashMap<>(draft.getDetails() != null ? draft.getDetails() : Map.of());
        putIfPresent(details, "firstName", request.getFirstName());
        putIfPresent(details, "lastName", request.getLastName());
        putIfPresent(details, "dateOfBirth",
                request.getDateOfBirth() != null ? request.getDateOfBirth().toString() : null);
        putIfPresent(details, "idNumber", request.getIdNumber());
        putIfPresent(details, "idType", request.getIdType());
        putIfPresent(details, "nationality", request.getNationality());
        putIfPresent(details, "countryOfResidence", request.getCountryOfResidence());
        putIfPresent(details, "phone", request.getPhone());
        putIfPresent(details, "email", request.getEmail());
        draft.setDetails(details);

        return toResponse(draftRepository.save(draft));
    }

    public KycDraftResponseDto uploadDocument(WidgetSessionContext context, String draftId,
                                              String documentType, MultipartFile file) {
        KycDraft draft = requireEditableDraft(context, draftId);
        String type = normalizeDocumentType(documentType);
        validateFile(type, file);

        String key = storageKey(draftId, type);
        try (InputStream in = file.getInputStream()) {
            documentStorage.put(key, in, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new ApplicationExceptions.IntactBadRequestException("Failed to read uploaded file");
        }

        KycDraftDocument document = documentRepository.findByDraftIdAndDocumentType(draftId, type)
                .orElseGet(KycDraftDocument::new);
        document.setDraftId(draftId);
        document.setDocumentType(type);
        document.setStorageKey(key);
        document.setMimeType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setOriginalFilename(sanitizeFilename(file.getOriginalFilename(), type));
        document.setUploadedAt(LocalDateTime.now());
        documentRepository.save(document);

        return toResponse(draft);
    }

    public KycDraftResponseDto deleteDocument(WidgetSessionContext context, String draftId, String documentType) {
        KycDraft draft = requireEditableDraft(context, draftId);
        String type = normalizeDocumentType(documentType);

        documentRepository.findByDraftIdAndDocumentType(draftId, type).ifPresent(document -> {
            documentRepository.delete(document);
            documentStorage.delete(document.getStorageKey());
        });
        return toResponse(draft);
    }

    public KycVerificationResponseDto submitDraft(WidgetSessionContext context, String draftId) {
        requireEditableDraft(context, draftId);

        // Lock first so no upload can change the files between reading and submitting them
        if (draftRepository.transitionStatus(draftId, KycDraftStatus.IN_PROGRESS, KycDraftStatus.SUBMITTING,
                LocalDateTime.now()) == 0) {
            throw new ApplicationExceptions.InvalidActionException("Draft is already being submitted");
        }

        KycVerificationResponseDto result;
        try {
            KycDraft draft = draftRepository.findById(draftId)
                    .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Draft not found"));
            KycVerificationRequestDto request = buildVerificationRequest(draft);
            Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();
            result = verificationService.submitVerification(
                    draft.getTier(), request, company, draft.getEnvironment());
        } catch (RuntimeException e) {
            draftRepository.transitionStatus(draftId, KycDraftStatus.SUBMITTING, KycDraftStatus.IN_PROGRESS,
                    LocalDateTime.now());
            throw e;
        }

        completeSubmission(draftId, result.getVerificationId());
        return result;
    }

    public void cancelDraft(WidgetSessionContext context, String draftId) {
        requireEmbedded(context);
        KycDraft draft = requireAccessibleDraft(context, draftId);
        if (draft.getStatus() == KycDraftStatus.SUBMITTING) {
            throw new ApplicationExceptions.InvalidActionException("Draft is being submitted");
        }
        deleteDraft(draftId);
        log.info("KYC draft {} cancelled", draftId);
    }

    public void deleteDraft(String draftId) {
        documentStorage.deletePrefix(draftPrefix(draftId));
        // Documents and handoffs are removed by ON DELETE CASCADE
        draftRepository.deleteById(draftId);
    }

    public KycDraft requireAccessibleDraft(WidgetSessionContext context, String draftId) {
        if (context.draftId() == null || !context.draftId().equals(draftId)) {
            throw new ApplicationExceptions.IntactForbiddenException("Token is not valid for this draft");
        }
        KycDraft draft = draftRepository.findById(draftId)
                .filter(d -> d.getCompanyId().equals(context.companyId()))
                .filter(d -> !d.isExpired())
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "Draft not found or expired"));

        if (context.channel() == WidgetChannel.HANDOFF && !isHandoffActive(context.handoffId(), draftId)) {
            throw new ApplicationExceptions.IntactForbiddenException(
                    "This phone session has ended. Scan a new code to continue.");
        }
        return draft;
    }

    public KycDraft requireEditableDraft(WidgetSessionContext context, String draftId) {
        KycDraft draft = requireAccessibleDraft(context, draftId);
        if (draft.getStatus() != KycDraftStatus.IN_PROGRESS) {
            throw new ApplicationExceptions.InvalidActionException("Draft can no longer be changed");
        }
        return draft;
    }

    public void requireEmbedded(WidgetSessionContext context) {
        if (context.channel() != WidgetChannel.EMBEDDED) {
            throw new ApplicationExceptions.IntactForbiddenException("Not allowed from a phone session");
        }
    }

    public KycDraftSessionResponseDto sessionResponse(KycDraft draft, WidgetChannel channel, String handoffId) {
        KycDraftSessionResponseDto response = KycDraftSessionResponseDto.builder()
                .draft(toResponse(draft))
                .build();

        if (draft.getStatus() == KycDraftStatus.IN_PROGRESS) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime tokenExpiry = now.plus(config.getTokenTtl());
            if (tokenExpiry.isAfter(draft.getExpiresAt())) {
                tokenExpiry = draft.getExpiresAt();
            }
            WidgetSessionContext tokenContext = new WidgetSessionContext(
                    draft.getCompanyId(), draft.getEnvironment(), draft.getTier(), draft.getExternalReference(),
                    draft.getId(), handoffId, channel);
            response.setDraftToken(kycSessionService.issueToken(
                    tokenContext, Date.from(tokenExpiry.atZone(ZoneId.systemDefault()).toInstant())));
            response.setDraftTokenExpiresInSeconds(Duration.between(now, tokenExpiry).getSeconds());
        }
        return response;
    }

    public KycDraftResponseDto toResponse(KycDraft draft) {
        List<String> documents = documentRepository.findByDraftId(draft.getId()).stream()
                .map(KycDraftDocument::getDocumentType)
                .sorted()
                .toList();

        KycDraftResponseDto.HandoffSummary handoff = handoffRepository
                .findFirstByDraftIdOrderByCreatedAtDesc(draft.getId())
                .map(h -> KycDraftResponseDto.HandoffSummary.builder()
                        .handoffId(h.getId())
                        .status(effectiveStatus(h))
                        .expiresAt(h.getExpiresAt())
                        .claimedAt(h.getClaimedAt())
                        .build())
                .orElse(null);

        return KycDraftResponseDto.builder()
                .draftId(draft.getId())
                .status(draft.getStatus().name())
                .tier(draft.getTier())
                .environment(draft.getEnvironment().name())
                .expiresAt(draft.getExpiresAt())
                .verificationId(draft.getVerificationId())
                .details(maskDetails(draft.getDetails()))
                .documents(documents)
                .handoff(handoff)
                .build();
    }

    private void completeSubmission(String draftId, String verificationId) {
        draftRepository.findById(draftId).ifPresent(draft -> {
            draft.setStatus(KycDraftStatus.SUBMITTED);
            draft.setVerificationId(verificationId);
            draft.setDetails(new HashMap<>());
            draftRepository.save(draft);
        });
        // The verification keeps its own copies; drop the draft's PII
        documentRepository.deleteAllByDraftId(draftId);
        documentStorage.deletePrefix(draftPrefix(draftId));
        log.info("KYC draft {} submitted as verification {}", draftId, verificationId);
    }

    private KycVerificationRequestDto buildVerificationRequest(KycDraft draft) {
        Map<String, Object> details = draft.getDetails() != null ? draft.getDetails() : Map.of();

        KycVerificationRequestDto request = new KycVerificationRequestDto();
        request.setFirstName(requireDetail(details, "firstName"));
        request.setLastName(requireDetail(details, "lastName"));
        request.setDateOfBirth(parseDate(requireDetail(details, "dateOfBirth")));
        request.setIdNumber(requireDetail(details, "idNumber"));
        request.setIdType(detail(details, "idType"));
        request.setNationality(detail(details, "nationality"));
        request.setCountryOfResidence(detail(details, "countryOfResidence"));
        request.setPhone(detail(details, "phone"));
        request.setEmail(detail(details, "email"));
        request.setVerificationType("individual");
        request.setExternalReference(draft.getExternalReference());

        TreeMap<Integer, KycDraftDocument> livenessByIndex = new TreeMap<>();
        for (KycDraftDocument document : documentRepository.findByDraftId(draft.getId())) {
            MultipartFile file = new StoredDocumentMultipartFile(documentStorage, document);
            Matcher liveness = LIVENESS_TYPE.matcher(document.getDocumentType());
            if (liveness.matches()) {
                livenessByIndex.put(Integer.parseInt(liveness.group(1)), document);
                continue;
            }
            switch (document.getDocumentType()) {
                case "selfie" -> request.setSelfie(file);
                case "id_front" -> request.setIdFront(file);
                case "id_back" -> request.setIdBack(file);
                case "proof_of_address" -> request.setProofOfAddress(file);
                default -> log.warn("Ignoring unknown draft document type {}", document.getDocumentType());
            }
        }

        // Drafts must carry the active-liveness frames; the single-photo fallback is for API callers only
        if (livenessByIndex.isEmpty()) {
            throw new ApplicationExceptions.IntactBadRequestException("Complete the liveness check before submitting");
        }
        List<MultipartFile> frames = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        livenessByIndex.values().forEach(document -> {
            Matcher m = LIVENESS_TYPE.matcher(document.getDocumentType());
            m.matches();
            frames.add(new StoredDocumentMultipartFile(documentStorage, document));
            labels.add(m.group(2));
        });
        request.setLivenessFrames(frames);
        request.setLivenessLabels(String.join(",", labels));
        return request;
    }

    private boolean isHandoffActive(String handoffId, String draftId) {
        return handoffId != null && handoffRepository.findById(handoffId)
                .filter(h -> h.getDraftId().equals(draftId) && h.getStatus() == KycHandoffStatus.CLAIMED)
                .isPresent();
    }

    private void validateFile(String type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationExceptions.IntactBadRequestException("file is required");
        }
        if (file.getSize() > config.getMaxDocumentSizeBytes()) {
            throw new ApplicationExceptions.IntactBadRequestException(type + " exceeds maximum size of 10MB");
        }
        String contentType = file.getContentType();
        boolean isImage = contentType != null && contentType.startsWith("image/");
        boolean isPdfProof = "proof_of_address".equals(type) && "application/pdf".equals(contentType);
        if (!isImage && !isPdfProof) {
            throw new ApplicationExceptions.IntactBadRequestException(type + " must be an image file");
        }
    }

    private String normalizeDocumentType(String documentType) {
        String type = documentType != null ? documentType.trim().toLowerCase() : "";
        if (!DOCUMENT_TYPES.contains(type) && !LIVENESS_TYPE.matcher(type).matches()) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    "Invalid document type. Must be one of: selfie, id_front, id_back, proof_of_address, "
                            + "liveness_{0-7}_{neutral|turn_left|turn_right}");
        }
        return type;
    }

    private String sanitizeFilename(String originalFilename, String fallback) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        String name = originalFilename.substring(Math.max(originalFilename.lastIndexOf('/'),
                originalFilename.lastIndexOf('\\')) + 1);
        name = UNSAFE_FILENAME_CHARS.matcher(name).replaceAll("_");
        if (name.length() > 100) {
            name = name.substring(name.length() - 100);
        }
        return name.isBlank() || name.chars().allMatch(c -> c == '.') ? fallback : name;
    }

    private String effectiveStatus(KycHandoff handoff) {
        if (handoff.getStatus() == KycHandoffStatus.PENDING && !handoff.isClaimable()) {
            return "EXPIRED";
        }
        return handoff.getStatus().name();
    }

    private Map<String, String> maskDetails(Map<String, Object> details) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (details == null) {
            return masked;
        }
        details.forEach((key, value) -> {
            if (value != null) {
                masked.put(key, mask(key, value.toString()));
            }
        });
        return masked;
    }

    private String mask(String key, String value) {
        return switch (key) {
            case "idType", "nationality", "countryOfResidence" -> value;
            case "idNumber" -> "****" + lastChars(value, 4);
            case "phone" -> "*******" + lastChars(value, 3);
            case "dateOfBirth" -> "****-**-**";
            case "email" -> maskEmail(value);
            default -> value.isEmpty() ? value : value.charAt(0) + "***";
        };
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    private String lastChars(String value, int count) {
        return value.length() <= count ? "" : value.substring(value.length() - count);
    }

    private String requireDetail(Map<String, Object> details, String key) {
        String value = detail(details, key);
        if (value == null) {
            throw new ApplicationExceptions.IntactBadRequestException(key + " is required");
        }
        return value;
    }

    private String detail(Map<String, Object> details, String key) {
        Object value = details.get(key);
        return value != null && !value.toString().isBlank() ? value.toString() : null;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApplicationExceptions.IntactBadRequestException("dateOfBirth is invalid");
        }
    }

    private static void putIfPresent(Map<String, Object> details, String key, String value) {
        if (value != null && !value.isBlank()) {
            details.put(key, value.trim());
        }
    }

    private static String draftPrefix(String draftId) {
        return "drafts/" + draftId;
    }

    private static String storageKey(String draftId, String documentType) {
        return draftPrefix(draftId) + "/" + documentType;
    }
}
