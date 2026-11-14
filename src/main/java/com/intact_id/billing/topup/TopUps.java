package com.intact_id.billing.topup;

import com.intact_id.accounts.CompanyRepository;
import com.intact_id.accounts.NotificationService;
import com.intact_id.billing.account.BillingAccounts;
import com.intact_id.billing.client.BillingApi;
import com.intact_id.checks.DocumentStorage;
import com.intact_id.util.exception.ApplicationExceptions;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wallet top-ups by bank transfer: the company submits the transfer with its receipt, Intact reviews it, and an
 * approved top-up credits the wallet in billing (external reference topup-<id>, so it is credited once).
 * Card, M-Pesa and Airtel Money will use the same records once the payment partner is connected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopUps {

    private static final long MAX_RECEIPT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> RECEIPT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

    private final WalletTopUpRepository topUps;
    private final BillingAccounts billing;
    private final DocumentStorage storage;
    private final NotificationService notifications;
    private final CompanyRepository companies;

    // Where companies send bank transfers; shown on the top-up form
    @Value("${billing.top-up.bank-instructions:}")
    private String bankInstructions;

    public record NewBankTopUp(BigDecimal amount, String bankName, String reference, LocalDate paidOn, String note) {
    }

    /** What the top-up form needs: the wallet's currency, where to pay, and whether top-ups are possible. */
    public Map<String, Object> instructions(String companyId) {
        BillingApi.Account account = billing.account(companyId);
        boolean prepaid = account.billingProfile() != null && account.billingProfile().paymentType() == BillingApi.PaymentType.PREPAID
                && account.wallet() != null;
        return Map.of(
                "currency", account.wallet() != null ? account.wallet().currency() : account.billingProfile() == null ? "" : account.billingProfile().currency(),
                "prepaid", prepaid,
                "bankInstructions", bankInstructions == null ? "" : bankInstructions,
                "methods", List.of(TopUpMethod.BANK_RECEIPT.name()));
    }

    /** A company reports a bank transfer, with its receipt; Intact staff are asked to review it. */
    @Transactional
    public WalletTopUp submitBankTransfer(String companyId, NewBankTopUp details, MultipartFile receipt, String submittedBy) {
        BillingApi.Account account = billing.account(companyId);
        if (account.wallet() == null || account.billingProfile() == null
                || account.billingProfile().paymentType() != BillingApi.PaymentType.PREPAID) {
            throw new ApplicationExceptions.IntactBadRequestException("Only prepaid accounts with a wallet can be topped up");
        }
        if (details.amount() == null || details.amount().signum() <= 0) {
            throw new ApplicationExceptions.IntactBadRequestException("Enter the amount you transferred");
        }
        if (blank(details.reference())) {
            throw new ApplicationExceptions.IntactBadRequestException("Enter the bank's transaction reference");
        }
        if (details.paidOn() == null || details.paidOn().isAfter(LocalDate.now())) {
            throw new ApplicationExceptions.IntactBadRequestException("Enter the date of the transfer (not in the future)");
        }
        String reference = details.reference().trim();
        if (topUps.referenceInUse(TopUpMethod.BANK_RECEIPT, reference)) {
            throw new ApplicationExceptions.IntactBadRequestException("A top-up with reference " + reference + " was already submitted");
        }
        String contentType = checkReceipt(receipt);

        WalletTopUp topUp = new WalletTopUp();
        topUp.setCompanyId(companyId);
        topUp.setMethod(TopUpMethod.BANK_RECEIPT);
        topUp.setStatus(TopUpStatus.PENDING_REVIEW);
        topUp.setAmount(details.amount());
        topUp.setCurrency(account.wallet().currency());
        topUp.setBankName(blank(details.bankName()) ? null : details.bankName().trim());
        topUp.setReference(reference);
        topUp.setPaidOn(details.paidOn());
        topUp.setNote(blank(details.note()) ? null : details.note().trim());
        topUp.setSubmittedBy(submittedBy);
        topUp = topUps.saveAndFlush(topUp);

        String key = "top-ups/" + topUp.getId() + "/receipt" + extension(contentType);
        try (InputStream in = receipt.getInputStream()) {
            storage.put(key, in, receipt.getSize(), contentType);
        } catch (IOException e) {
            throw new ApplicationExceptions.IntactBadRequestException("Could not read the receipt");
        }
        topUp.setReceiptStorageKey(key);
        topUp.setReceiptFilename(receipt.getOriginalFilename());
        topUp.setReceiptContentType(contentType);
        topUp.setReceiptSize(receipt.getSize());
        topUp = topUps.save(topUp);

        notifications.notifyStaff("Top-up to review",
                companyName(companyId) + " reports a bank transfer of " + topUp.getCurrency() + " " + topUp.getAmount().stripTrailingZeros().toPlainString()
                        + " (reference " + reference + ").", "INFO", "/dashboard/top-ups");
        log.info("Bank top-up {} submitted for company {}: {} {}", topUp.getId(), companyId, topUp.getCurrency(), topUp.getAmount());
        return topUp;
    }

    /** companyId null: every company's (super admins). status null: all. */
    public Page<WalletTopUp> list(String companyId, TopUpStatus status, int page, int size) {
        return topUps.search(companyId, status == null, status == null ? List.of(TopUpStatus.PENDING_REVIEW) : List.of(status),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    public long waitingForReview() {
        return topUps.countByStatus(TopUpStatus.PENDING_REVIEW);
    }

    public WalletTopUp get(String companyId, String id) {
        return topUps.findById(id).filter(t -> companyId == null || t.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Top-up not found"));
    }

    public InputStream receipt(WalletTopUp topUp) {
        if (!topUp.hasReceipt()) throw new ApplicationExceptions.IntactResourceNotFoundException("This top-up has no receipt");
        return storage.get(topUp.getReceiptStorageKey());
    }

    /**
     * Intact approves a bank top-up and the wallet is credited with {@code amount} (what the bank shows; the
     * submitted amount when null). If billing can't be reached the top-up stays pending, so it can be approved again.
     */
    @Transactional
    public WalletTopUp approve(String id, BigDecimal amount, String note, String reviewer) {
        WalletTopUp topUp = pendingReview(id);
        BigDecimal credit = amount == null ? topUp.getAmount() : amount;
        if (credit.signum() <= 0) throw new ApplicationExceptions.IntactBadRequestException("The amount to credit must be more than 0");

        String externalRef = "topup-" + topUp.getId();
        String description = "Bank transfer " + topUp.getReference() + (topUp.getBankName() != null ? " (" + topUp.getBankName() + ")" : "");
        try {
            billing.creditWallet(topUp.getCompanyId(), new BillingApi.WalletCredit(credit, externalRef, description));
        } catch (FeignException.Conflict e) {
            // Already credited by an earlier attempt that didn't finish here
            log.warn("Top-up {} was already credited in billing; recording it as approved", id);
        }
        LocalDateTime now = LocalDateTime.now();
        topUp.setStatus(TopUpStatus.APPROVED);
        topUp.setCreditedAmount(credit);
        topUp.setCreditedAt(now);
        topUp.setReviewedBy(reviewer);
        topUp.setReviewedAt(now);
        topUp.setReviewNote(blank(note) ? null : note.trim());
        topUp = topUps.save(topUp);
        notifyCompany(topUp, "Top-up approved", "Your wallet was credited with " + topUp.getCurrency() + " "
                + credit.stripTrailingZeros().toPlainString() + " for the bank transfer " + topUp.getReference() + ".");
        log.info("Top-up {} approved by {}: credited {} {}", id, reviewer, topUp.getCurrency(), credit);
        return topUp;
    }

    @Transactional
    public WalletTopUp reject(String id, String reason, String reviewer) {
        if (blank(reason)) throw new ApplicationExceptions.IntactBadRequestException("Say why the top-up is rejected; the company sees it");
        WalletTopUp topUp = pendingReview(id);
        topUp.setStatus(TopUpStatus.REJECTED);
        topUp.setReviewedBy(reviewer);
        topUp.setReviewedAt(LocalDateTime.now());
        topUp.setReviewNote(reason.trim());
        topUp = topUps.save(topUp);
        notifyCompany(topUp, "Top-up rejected", "The bank transfer " + topUp.getReference() + " was not credited: " + reason.trim());
        log.info("Top-up {} rejected by {}", id, reviewer);
        return topUp;
    }

    /** The company withdraws a top-up before it is reviewed. */
    @Transactional
    public WalletTopUp cancel(String companyId, String id) {
        WalletTopUp topUp = get(companyId, id);
        if (topUp.getStatus() != TopUpStatus.PENDING_REVIEW) {
            throw new ApplicationExceptions.IntactBadRequestException("Only a top-up waiting for review can be cancelled");
        }
        topUp.setStatus(TopUpStatus.CANCELLED);
        return topUps.save(topUp);
    }

    private WalletTopUp pendingReview(String id) {
        WalletTopUp topUp = get(null, id);
        if (topUp.getStatus() != TopUpStatus.PENDING_REVIEW) {
            throw new ApplicationExceptions.IntactBadRequestException("This top-up was already " + topUp.getStatus().name().toLowerCase().replace('_', ' '));
        }
        return topUp;
    }

    private void notifyCompany(WalletTopUp topUp, String title, String message) {
        try {
            notifications.persistNotification(topUp.getCompanyId(), title, message, "INFO");
        } catch (RuntimeException e) {
            log.warn("Could not notify company {} about top-up {}: {}", topUp.getCompanyId(), topUp.getId(), e.getMessage());
        }
    }

    /** PDF or image, at most 10 MB, and the file really is what it says. Returns the content type. */
    private static String checkReceipt(MultipartFile receipt) {
        if (receipt == null || receipt.isEmpty()) throw new ApplicationExceptions.IntactBadRequestException("Attach the bank receipt");
        if (receipt.getSize() > MAX_RECEIPT_BYTES) throw new ApplicationExceptions.IntactBadRequestException("The receipt must be 10 MB or smaller");
        String type = receipt.getContentType() == null ? "" : receipt.getContentType().toLowerCase();
        if (!RECEIPT_TYPES.contains(type)) {
            throw new ApplicationExceptions.IntactBadRequestException("The receipt must be a PDF, JPEG, PNG or WebP file");
        }
        byte[] head = new byte[12];
        try (InputStream in = receipt.getInputStream()) {
            int read = in.readNBytes(head, 0, head.length);
            if (!matches(type, head, read)) throw new ApplicationExceptions.IntactBadRequestException("The receipt file is not a valid " + type);
        } catch (IOException e) {
            throw new ApplicationExceptions.IntactBadRequestException("Could not read the receipt");
        }
        return type;
    }

    private static boolean matches(String type, byte[] b, int n) {
        return switch (type) {
            case "application/pdf" -> n >= 4 && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("%PDF");
            case "image/jpeg" -> n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/png" -> n >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/webp" -> n >= 12 && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }

    private static String extension(String type) {
        return switch (type) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
    }

    private String companyName(String companyId) {
        return companies.findById(companyId).map(c -> c.getTradingName() != null ? c.getTradingName() : c.getLegalName()).orElse("A company");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
