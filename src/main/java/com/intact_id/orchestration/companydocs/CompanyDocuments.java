package com.intact_id.orchestration.companydocs;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.checks.DocumentStorage;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A company's compliance documents for onboarding with Intact ID: the company uploads each one (a new upload is the
 * next version), Intact staff approve or reject it, and documents with an expiry date flag themselves before they lapse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyDocuments {

    /** An approved document counts as expiring this many days before its expiry date. */
    static final int EXPIRING_WITHIN_DAYS = 30;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final CompanyDocumentRepository documents;
    private final DocumentStorage storage;

    /** One row per document type: its latest version, or nothing yet. */
    public record Row(String type, String label, String category, boolean expires, String status, Version latest,
                      List<Version> history) {
    }

    public record Version(String id, int version, String fileName, String contentType, long sizeBytes, String status,
                          LocalDate expiresOn, String uploadedBy, LocalDateTime uploadedAt, String reviewNote,
                          String reviewedBy, LocalDateTime reviewedAt) {
    }

    public record Readiness(int required, int approved, int awaitingReview, int actionNeeded) {
    }

    public record Overview(Readiness readiness, List<Row> documents) {
    }

    public record Download(String fileName, String contentType, long size, InputStream content) {
    }

    public Overview overview(String companyId) {
        Map<CompanyDocumentType, List<CompanyDocument>> byType = new LinkedHashMap<>();
        for (CompanyDocument d : documents.findByCompanyIdOrderByTypeAscVersionDesc(companyId)) {
            byType.computeIfAbsent(d.getType(), t -> new ArrayList<>()).add(d);
        }
        List<Row> rows = new ArrayList<>();
        int approved = 0, awaiting = 0, action = 0;
        for (CompanyDocumentType type : CompanyDocumentType.values()) {
            List<CompanyDocument> versions = byType.getOrDefault(type, List.of());
            CompanyDocument latest = versions.isEmpty() ? null : versions.get(0);
            String status = statusOf(latest);
            switch (status) {
                case "APPROVED", "EXPIRING" -> approved++;
                case "PENDING_REVIEW" -> awaiting++;
                default -> { }
            }
            if (!status.equals("APPROVED") && !status.equals("PENDING_REVIEW")) action++;
            rows.add(new Row(type.name(), type.label(), type.category(), type.expires(), status,
                    latest == null ? null : view(latest), versions.stream().map(this::view).toList()));
        }
        return new Overview(new Readiness(CompanyDocumentType.values().length, approved, awaiting, action), rows);
    }

    @Transactional
    public Version upload(String companyId, String type, MultipartFile file, LocalDate expiresOn) {
        CompanyDocumentType documentType = parse(type);
        if (file == null || file.isEmpty()) throw new ApplicationExceptions.IntactBadRequestException("Choose a file to upload");
        if (file.getSize() > MAX_BYTES) throw new ApplicationExceptions.IntactBadRequestException("Files can be up to 10 MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType) || !contentMatches(file, contentType)) {
            throw new ApplicationExceptions.IntactBadRequestException("Upload a PDF, JPG or PNG");
        }
        if (documentType.expires() && expiresOn == null) {
            throw new ApplicationExceptions.IntactBadRequestException(documentType.label() + " needs its expiry date");
        }
        if (expiresOn != null && expiresOn.isBefore(LocalDate.now())) {
            throw new ApplicationExceptions.IntactBadRequestException("That document has already expired");
        }

        int version = documents.findFirstByCompanyIdAndTypeOrderByVersionDesc(companyId, documentType)
                .map(d -> d.getVersion() + 1).orElse(1);
        String fileName = safeName(file.getOriginalFilename());
        String key = "company-documents/" + companyId + "/" + documentType.name().toLowerCase() + "/v" + version + "-" + fileName;
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new ApplicationExceptions.IntactInternalServerException("Could not store the file");
        }

        CompanyDocument d = new CompanyDocument();
        d.setCompanyId(companyId);
        d.setType(documentType);
        d.setVersion(version);
        d.setFileName(fileName);
        d.setContentType(contentType);
        d.setSizeBytes(file.getSize());
        d.setStorageKey(key);
        d.setStatus(CompanyDocument.Status.PENDING_REVIEW);
        d.setExpiresOn(documentType.expires() ? expiresOn : null);
        d.setUploadedBy(SecurityContextUtil.getAuthenticatedUser().getUsername());
        d.setUploadedAt(LocalDateTime.now());
        log.info("Company {} uploaded {} v{}", companyId, documentType, version);
        return view(documents.save(d));
    }

    public Download download(String companyId, String documentId) {
        CompanyDocument d = owned(companyId, documentId);
        return new Download(d.getFileName(), d.getContentType(), d.getSizeBytes(), storage.get(d.getStorageKey()));
    }

    /** Intact staff approve or reject a version; a rejection says why, so the company knows what to fix. */
    @Transactional
    public Version review(String companyId, String documentId, boolean approve, String note) {
        CompanyDocument d = owned(companyId, documentId);
        if (d.getStatus() != CompanyDocument.Status.PENDING_REVIEW) {
            throw new ApplicationExceptions.IntactBadRequestException("This version has already been reviewed");
        }
        String reason = note == null ? "" : note.trim();
        if (!approve && reason.isEmpty()) {
            throw new ApplicationExceptions.IntactBadRequestException("Say why it's rejected, so the company knows what to fix");
        }
        d.setStatus(approve ? CompanyDocument.Status.APPROVED : CompanyDocument.Status.REJECTED);
        d.setReviewNote(reason.isEmpty() ? null : reason);
        d.setReviewedBy(SecurityContextUtil.getAuthenticatedUser().getUsername());
        d.setReviewedAt(LocalDateTime.now());
        log.info("{} v{} of company {} {}", d.getType(), d.getVersion(), companyId, approve ? "approved" : "rejected");
        return view(documents.save(d));
    }

    /** MISSING, PENDING_REVIEW, APPROVED, EXPIRING, EXPIRED or REJECTED. */
    static String statusOf(CompanyDocument latest) {
        if (latest == null) return "MISSING";
        if (latest.getStatus() == CompanyDocument.Status.APPROVED && latest.getExpiresOn() != null) {
            LocalDate today = LocalDate.now();
            if (latest.getExpiresOn().isBefore(today)) return "EXPIRED";
            if (!latest.getExpiresOn().isAfter(today.plusDays(EXPIRING_WITHIN_DAYS))) return "EXPIRING";
        }
        return latest.getStatus().name();
    }

    private CompanyDocument owned(String companyId, String documentId) {
        return documents.findById(documentId)
                .filter(d -> d.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Document not found"));
    }

    private Version view(CompanyDocument d) {
        return new Version(d.getId(), d.getVersion(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getStatus().name(), d.getExpiresOn(), d.getUploadedBy(), d.getUploadedAt(), d.getReviewNote(),
                d.getReviewedBy(), d.getReviewedAt());
    }

    private static CompanyDocumentType parse(String type) {
        try {
            return CompanyDocumentType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApplicationExceptions.IntactBadRequestException("Unknown document type: " + type);
        }
    }

    /** The file starts the way its declared type does, so a renamed file of another kind is refused. */
    private static boolean contentMatches(MultipartFile file, String contentType) {
        byte[] start = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(start, 0, start.length);
        } catch (IOException e) {
            return false;
        }
        return switch (contentType) {
            case "application/pdf" -> read >= 4 && start[0] == '%' && start[1] == 'P' && start[2] == 'D' && start[3] == 'F';
            case "image/jpeg" -> read >= 3 && (start[0] & 0xFF) == 0xFF && (start[1] & 0xFF) == 0xD8 && (start[2] & 0xFF) == 0xFF;
            case "image/png" -> read >= 8 && (start[0] & 0xFF) == 0x89 && start[1] == 'P' && start[2] == 'N' && start[3] == 'G';
            default -> false;
        };
    }

    /** Keeps the name readable but safe as part of a storage key. */
    private static String safeName(String original) {
        String name = original == null ? "document" : original.replaceAll("^.*[/\\\\]", "");
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.isEmpty() || name.startsWith(".")) name = "document" + name;
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
