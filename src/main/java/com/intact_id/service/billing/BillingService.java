package com.intact_id.service.billing;

import com.intact_id.config.TiersConfig;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.constant.enums.billing.BillingChargeStatus;
import com.intact_id.constant.enums.billing.BillingEvent;
import com.intact_id.constant.enums.billing.BillingInvoiceStatus;
import com.intact_id.constant.enums.billing.BillingMode;
import com.intact_id.constant.enums.billing.WalletTransactionType;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyCommunicationDetails;
import com.intact_id.db.model.billing.BillingCharge;
import com.intact_id.db.model.billing.BillingInvoice;
import com.intact_id.db.model.billing.BillingInvoiceLine;
import com.intact_id.db.model.billing.CompanyBillingProfile;
import com.intact_id.db.model.billing.WalletTransaction;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.CompanyCommunicationRepository;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.db.repository.billing.BillingChargeRepository;
import com.intact_id.db.repository.billing.BillingInvoiceLineRepository;
import com.intact_id.db.repository.billing.BillingInvoiceRepository;
import com.intact_id.db.repository.billing.CompanyBillingProfileRepository;
import com.intact_id.db.repository.billing.WalletTransactionRepository;
import com.intact_id.dto.email.EmailDTO;
import com.intact_id.dto.request.billing.CompanyBillingProfileRequestDto;
import com.intact_id.dto.request.billing.InvoiceGenerationRequestDto;
import com.intact_id.dto.request.billing.WalletTopUpRequestDto;
import com.intact_id.dto.response.billing.BillingInvoiceLineResponseDto;
import com.intact_id.dto.response.billing.BillingInvoiceResponseDto;
import com.intact_id.dto.response.billing.BillingSummaryResponseDto;
import com.intact_id.dto.response.billing.CompanyBillingProfileResponseDto;
import com.intact_id.dto.response.billing.WalletTransactionResponseDto;
import com.intact_id.service.email.EmailService;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final CompanyRepository companyRepository;
    private final CompanyCommunicationRepository companyCommunicationRepository;
    private final CompanyBillingProfileRepository billingProfileRepository;
    private final BillingChargeRepository billingChargeRepository;
    private final BillingInvoiceRepository billingInvoiceRepository;
    private final BillingInvoiceLineRepository billingInvoiceLineRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final EmailService emailService;
    private final TiersConfig tiersConfig;

    @Transactional(readOnly = true)
    public CompanyBillingProfileResponseDto getCompanyBillingProfile(String companyId) {
        CompanyBillingProfile profile = getBillingProfileEntity(companyId);
        return toProfileResponse(profile);
    }

    @Transactional
    public CompanyBillingProfileResponseDto upsertCompanyBillingProfile(String companyId,
            CompanyBillingProfileRequestDto request) {
        Company company = getCompany(companyId);
        String normalizedTierCode = normalizeTierCode(request.getBillingTierCode());
        validateTierExists(normalizedTierCode);

        CompanyBillingProfile profile = billingProfileRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    CompanyBillingProfile created = new CompanyBillingProfile();
                    created.setCompany(company);
                    return created;
                });

        profile.setBillingTierCode(normalizedTierCode);
        profile.setBillingMode(request.getBillingMode());
        profile.setUnitPrice(scale(request.getUnitPrice()));
        profile.setCurrency(normalizeCurrency(request.getCurrency()));
        profile.setBillingEmail(blankToNull(request.getBillingEmail()));
        profile.setBillOnSubmission(Boolean.TRUE.equals(request.getBillOnSubmission()));
        profile.setBillOnCompleted(defaultTrue(request.getBillOnCompleted()));
        profile.setBillOnRejected(defaultTrue(request.getBillOnRejected()));
        profile.setBillOnFailedNonSystem(defaultTrue(request.getBillOnFailedNonSystem()));
        profile.setBillOnFailedSystem(Boolean.TRUE.equals(request.getBillOnFailedSystem()));
        profile.setLowBalanceThreshold(scale(defaultDecimal(request.getLowBalanceThreshold())));
        profile.setCreditLimit(scale(defaultDecimal(request.getCreditLimit())));
        profile.setInvoiceDueDays(request.getInvoiceDueDays() != null ? request.getInvoiceDueDays() : 30);
        profile.setStatus(request.getStatus() != null ? request.getStatus() : GlobalStatuses.ACTIVE);
        profile.setEffectiveFrom(request.getEffectiveFrom());
        profile.setEffectiveTo(request.getEffectiveTo());

        if (profile.getCurrentBalance() == null) {
            profile.setCurrentBalance(BigDecimal.ZERO);
        }

        CompanyBillingProfile saved = billingProfileRepository.save(profile);
        return toProfileResponse(saved);
    }

    @Transactional
    public WalletTransactionResponseDto topUpWallet(String companyId, WalletTopUpRequestDto request) {
        CompanyBillingProfile profile = getBillingProfileEntity(companyId);
        if (profile.getBillingMode() != BillingMode.PREPAID) {
            throw new ApplicationExceptions.InvalidActionException("Wallet top-up is only supported for PREPAID companies");
        }

        BigDecimal amount = scale(request.getAmount());
        BigDecimal before = defaultDecimal(profile.getCurrentBalance());
        BigDecimal after = before.add(amount);

        profile.setCurrentBalance(after);
        billingProfileRepository.save(profile);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setCompany(profile.getCompany());
        transaction.setBillingProfile(profile);
        transaction.setType(WalletTransactionType.TOPUP);
        transaction.setAmount(amount);
        transaction.setCurrency(normalizeCurrency(request.getCurrency() != null ? request.getCurrency() : profile.getCurrency()));
        transaction.setReference(blankToNull(request.getReference()));
        transaction.setDescription(blankToNull(request.getDescription()) != null
                ? request.getDescription()
                : "Manual wallet top-up");
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(after);

        return toWalletResponse(walletTransactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public BillingSummaryResponseDto getBillingSummary(String companyId) {
        CompanyBillingProfile profile = getBillingProfileEntity(companyId);
        List<BillingCharge> charges = billingChargeRepository.findByCompanyIdAndStatusInAndChargeableAtBetween(
                companyId,
                List.of(BillingChargeStatus.READY, BillingChargeStatus.DEBITED, BillingChargeStatus.INVOICED),
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.now().plusYears(10));

        BigDecimal ready = sumCharges(charges, BillingChargeStatus.READY);
        BigDecimal debited = sumCharges(charges, BillingChargeStatus.DEBITED);
        BigDecimal invoiced = sumCharges(charges, BillingChargeStatus.INVOICED);
        BigDecimal currentBalance = defaultDecimal(profile.getCurrentBalance());
        BigDecimal creditLimit = defaultDecimal(profile.getCreditLimit());
        BigDecimal walletBalance = currentBalance.max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal usedCredit = currentBalance.signum() < 0
                ? currentBalance.abs().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal remainingCredit = creditLimit.subtract(usedCredit).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal availableToSpend = walletBalance.add(remainingCredit).setScale(4, RoundingMode.HALF_UP);
        BigDecimal outstandingAmount = profile.getBillingMode() == BillingMode.POSTPAID
                ? ready.add(invoiced).setScale(4, RoundingMode.HALF_UP)
                : usedCredit;
        Integer estimatedSubmissionsRemaining = null;
        if (profile.getBillingMode() == BillingMode.PREPAID
                && profile.getUnitPrice() != null
                && profile.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            estimatedSubmissionsRemaining = availableToSpend
                    .divide(profile.getUnitPrice(), 0, RoundingMode.DOWN)
                    .intValue();
        }

        return BillingSummaryResponseDto.builder()
                .companyId(companyId)
                .billingTierCode(profile.getBillingTierCode())
                .billingMode(profile.getBillingMode())
                .currency(profile.getCurrency())
                .unitPrice(profile.getUnitPrice())
                .currentBalance(currentBalance)
                .walletBalance(walletBalance)
                .usedCredit(usedCredit)
                .remainingCredit(remainingCredit)
                .availableToSpend(availableToSpend)
                .outstandingAmount(outstandingAmount)
                .readyToInvoiceAmount(ready)
                .debitedAmount(debited)
                .invoicedAmount(invoiced)
                .estimatedSubmissionsRemaining(estimatedSubmissionsRemaining)
                .build();
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponseDto> getWalletTransactions(String companyId) {
        getBillingProfileEntity(companyId);
        return walletTransactionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toWalletResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BillingInvoiceResponseDto> getInvoices(String companyId) {
        return billingInvoiceRepository.findByCompanyIdOrderByGeneratedAtDesc(companyId).stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    @Transactional
    public BillingInvoiceResponseDto generateInvoice(String companyId, InvoiceGenerationRequestDto request) {
        CompanyBillingProfile profile = getBillingProfileEntity(companyId);
        if (profile.getBillingMode() != BillingMode.POSTPAID) {
            throw new ApplicationExceptions.InvalidActionException("Invoice generation is only supported for POSTPAID companies");
        }

        LocalDateTime periodStart = request.getPeriodStart().atStartOfDay();
        LocalDateTime periodEnd = request.getPeriodEnd().plusDays(1).atStartOfDay().minusNanos(1);

        List<BillingCharge> charges = billingChargeRepository.findByCompanyIdAndStatusInAndChargeableAtBetween(
                companyId,
                List.of(BillingChargeStatus.READY),
                periodStart,
                periodEnd);

        if (charges.isEmpty()) {
            throw new ApplicationExceptions.InvalidActionException("No billable charges found for the requested period");
        }

        BillingInvoice invoice = new BillingInvoice();
        invoice.setCompany(profile.getCompany());
        invoice.setBillingProfile(profile);
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setPeriodStart(request.getPeriodStart());
        invoice.setPeriodEnd(request.getPeriodEnd());
        invoice.setBillingMode(profile.getBillingMode());
        invoice.setCurrency(profile.getCurrency());
        invoice.setGeneratedAt(LocalDateTime.now());
        invoice.setDueAt(LocalDate.now().plusDays(profile.getInvoiceDueDays()));
        invoice.setRecipientEmail(resolveInvoiceRecipient(profile.getCompany(), profile));

        BillingInvoice savedInvoice = billingInvoiceRepository.save(invoice);

        Map<String, List<BillingCharge>> grouped = charges.stream()
                .sorted(Comparator.comparing(BillingCharge::getChargeableAt))
                .collect(Collectors.groupingBy(charge -> charge.getBillingTierCode() + "|" + charge.getUnitPrice()));

        BigDecimal subtotal = BigDecimal.ZERO;
        List<BillingInvoiceLine> lines = new ArrayList<>();

        for (Map.Entry<String, List<BillingCharge>> entry : grouped.entrySet()) {
            List<BillingCharge> groupedCharges = entry.getValue();
            BillingCharge sample = groupedCharges.get(0);
            BigDecimal quantity = BigDecimal.valueOf(groupedCharges.size());
            BigDecimal amount = scale(sample.getUnitPrice().multiply(quantity));

            BillingInvoiceLine line = new BillingInvoiceLine();
            line.setInvoice(savedInvoice);
            line.setLineType("VERIFICATION");
            line.setDescription("Verification charges for " + sample.getBillingTierCode());
            line.setQuantity(quantity);
            line.setUnitPrice(sample.getUnitPrice());
            line.setAmount(amount);
            lines.add(line);

            subtotal = subtotal.add(amount);
        }

        billingInvoiceLineRepository.saveAll(lines);

        savedInvoice.setSubtotal(scale(subtotal));
        savedInvoice.setTax(BigDecimal.ZERO);
        savedInvoice.setTotal(scale(subtotal));
        BillingInvoice finalizedInvoice = billingInvoiceRepository.save(savedInvoice);

        for (BillingCharge charge : charges) {
            charge.setInvoice(finalizedInvoice);
            charge.setStatus(BillingChargeStatus.INVOICED);
            charge.setChargedAt(LocalDateTime.now());
        }
        billingChargeRepository.saveAll(charges);

        return toInvoiceResponse(finalizedInvoice);
    }

    @Transactional
    public BillingInvoiceResponseDto sendInvoice(String invoiceId, String overrideRecipientEmail) {
        BillingInvoice invoice = billingInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Invoice not found: " + invoiceId));

        Company company = invoice.getCompany();
        CompanyBillingProfile profile = invoice.getBillingProfile();
        String recipient = blankToNull(overrideRecipientEmail);
        if (recipient == null) {
            recipient = resolveInvoiceRecipient(company, profile);
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("companyName", company.getLegalName());
        variables.put("invoiceNumber", invoice.getInvoiceNumber());
        variables.put("periodStart", invoice.getPeriodStart());
        variables.put("periodEnd", invoice.getPeriodEnd());
        variables.put("currency", invoice.getCurrency());
        variables.put("total", invoice.getTotal());
        variables.put("dueAt", invoice.getDueAt());
        variables.put("lineItems", billingInvoiceLineRepository.findByInvoiceId(invoiceId).stream()
                .map(line -> Map.<String, Object>of(
                        "description", line.getDescription(),
                        "quantity", line.getQuantity(),
                        "unitPrice", line.getUnitPrice(),
                        "amount", line.getAmount()))
                .toList());

        EmailDTO emailDTO = EmailDTO.builder()
                .to(recipient)
                .subject("Intact ID Invoice " + invoice.getInvoiceNumber())
                .templateName("billing-invoice")
                .variables(variables)
                .build();

        boolean sent = emailService.sendTemplatedEmail(emailDTO);
        if (!sent) {
            throw new ApplicationExceptions.IntactInternalServerException("Failed to send invoice email");
        }

        invoice.setRecipientEmail(recipient);
        invoice.setStatus(BillingInvoiceStatus.SENT);
        invoice.setSentAt(LocalDateTime.now());
        return toInvoiceResponse(billingInvoiceRepository.save(invoice));
    }

    public record InsufficientBalance(BigDecimal available, BigDecimal required) {}

    @Transactional(readOnly = true)
    public InsufficientBalance checkSufficientBalance(VerificationRequest verificationRequest) {
        Optional<CompanyBillingProfile> optionalProfile = billingProfileRepository
                .findByCompanyId(verificationRequest.getCompany().getId());
        if (optionalProfile.isEmpty()) return null;

        CompanyBillingProfile profile = optionalProfile.get();
        if (profile.getBillingMode() != BillingMode.PREPAID) return null;
        if (profile.getUnitPrice() == null || profile.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) return null;

        BigDecimal currentBalance = defaultDecimal(profile.getCurrentBalance());
        BigDecimal creditLimit = defaultDecimal(profile.getCreditLimit());
        BigDecimal walletBalance = currentBalance.max(BigDecimal.ZERO);
        BigDecimal usedCredit = currentBalance.signum() < 0 ? currentBalance.abs() : BigDecimal.ZERO;
        BigDecimal remainingCredit = creditLimit.subtract(usedCredit).max(BigDecimal.ZERO);
        BigDecimal availableToSpend = walletBalance.add(remainingCredit);

        if (availableToSpend.compareTo(profile.getUnitPrice()) < 0) {
            return new InsufficientBalance(availableToSpend, profile.getUnitPrice());
        }
        return null;
    }

    @Transactional
    public void handleVerificationSubmitted(VerificationRequest verificationRequest) {
        Optional<CompanyBillingProfile> optionalProfile = billingProfileRepository
                .findByCompanyId(verificationRequest.getCompany().getId());
        if (optionalProfile.isEmpty()) {
            return;
        }

        CompanyBillingProfile profile = optionalProfile.get();
        if (Boolean.TRUE.equals(profile.getBillOnSubmission())) {
            createOrApplyCharge(profile, verificationRequest, BillingEvent.SUBMISSION, "Billed on submission");
        }
    }

    @Transactional
    public void handleVerificationOutcome(VerificationRequest verificationRequest) {
        Optional<CompanyBillingProfile> optionalProfile = billingProfileRepository
                .findByCompanyId(verificationRequest.getCompany().getId());
        if (optionalProfile.isEmpty()) {
            return;
        }

        CompanyBillingProfile profile = optionalProfile.get();
        if (billingChargeRepository.findByVerificationRequestId(verificationRequest.getId()).isPresent()) {
            return;
        }

        BillingEvent event = determineBillingEvent(verificationRequest);
        boolean billable = isBillable(profile, event);
        if (!billable) {
            createWaivedCharge(profile, verificationRequest, event, "Charge waived by billing profile rule");
            return;
        }

        createOrApplyCharge(profile, verificationRequest, event, "Billed by final verification outcome");
    }

    private BillingEvent determineBillingEvent(VerificationRequest verificationRequest) {
        if (verificationRequest.getStatus() == GlobalStatuses.FAILED) {
            return isSystemFailure(verificationRequest) ? BillingEvent.FAILURE_SYSTEM : BillingEvent.FAILURE_NON_SYSTEM;
        }

        if ("rejected".equalsIgnoreCase(verificationRequest.getOverallDecision())) {
            return BillingEvent.REJECTION;
        }

        return BillingEvent.COMPLETION;
    }

    private boolean isBillable(CompanyBillingProfile profile, BillingEvent event) {
        return switch (event) {
            case SUBMISSION -> Boolean.TRUE.equals(profile.getBillOnSubmission());
            case COMPLETION -> Boolean.TRUE.equals(profile.getBillOnCompleted());
            case REJECTION -> Boolean.TRUE.equals(profile.getBillOnRejected());
            case FAILURE_NON_SYSTEM -> Boolean.TRUE.equals(profile.getBillOnFailedNonSystem());
            case FAILURE_SYSTEM -> Boolean.TRUE.equals(profile.getBillOnFailedSystem());
        };
    }

    private boolean isSystemFailure(VerificationRequest verificationRequest) {
        return verificationRequest.getFailureReason() != null
                && "systemerror".equalsIgnoreCase(verificationRequest.getFailureReason().replace("_", ""));
    }

    private void createWaivedCharge(CompanyBillingProfile profile, VerificationRequest verificationRequest,
            BillingEvent event, String reason) {
        BillingCharge charge = new BillingCharge();
        charge.setCompany(profile.getCompany());
        charge.setBillingProfile(profile);
        charge.setVerificationRequest(verificationRequest);
        charge.setBillingEvent(event);
        charge.setStatus(BillingChargeStatus.WAIVED);
        charge.setBillingTierCode(profile.getBillingTierCode());
        charge.setCurrency(profile.getCurrency());
        charge.setUnitPrice(profile.getUnitPrice());
        charge.setAmount(BigDecimal.ZERO);
        charge.setDecisionReason(reason);
        charge.setChargeableAt(LocalDateTime.now());
        billingChargeRepository.save(charge);
    }

    private void createOrApplyCharge(CompanyBillingProfile profile, VerificationRequest verificationRequest,
            BillingEvent event, String reason) {
        BillingCharge charge = new BillingCharge();
        charge.setCompany(profile.getCompany());
        charge.setBillingProfile(profile);
        charge.setVerificationRequest(verificationRequest);
        charge.setBillingEvent(event);
        charge.setBillingTierCode(profile.getBillingTierCode());
        charge.setCurrency(profile.getCurrency());
        charge.setUnitPrice(profile.getUnitPrice());
        charge.setAmount(profile.getUnitPrice());
        charge.setDecisionReason(reason);
        charge.setChargeableAt(LocalDateTime.now());

        if (profile.getBillingMode() == BillingMode.PREPAID) {
            WalletTransaction transaction = debitWallet(profile, verificationRequest, profile.getUnitPrice(),
                    "Verification charge for " + verificationRequest.getId());
            charge.setStatus(BillingChargeStatus.DEBITED);
            charge.setChargedAt(LocalDateTime.now());
            charge.setDecisionReason(reason + " via wallet debit " + transaction.getId());
        } else {
            charge.setStatus(BillingChargeStatus.READY);
        }

        billingChargeRepository.save(charge);
    }

    private WalletTransaction debitWallet(CompanyBillingProfile profile, VerificationRequest verificationRequest,
            BigDecimal amount, String description) {
        BigDecimal before = defaultDecimal(profile.getCurrentBalance());
        BigDecimal after = before.subtract(amount);
        BigDecimal allowedMinimumBalance = defaultDecimal(profile.getCreditLimit()).negate();

        if (after.compareTo(allowedMinimumBalance) < 0) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Billing credit limit exceeded for company " + profile.getCompany().getId()
                            + ". Balance cannot go below " + allowedMinimumBalance);
        }

        profile.setCurrentBalance(scale(after));
        billingProfileRepository.save(profile);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setCompany(profile.getCompany());
        transaction.setBillingProfile(profile);
        transaction.setType(WalletTransactionType.DEBIT);
        transaction.setAmount(scale(amount));
        transaction.setCurrency(profile.getCurrency());
        transaction.setReference("verification:" + verificationRequest.getId());
        transaction.setDescription(description);
        transaction.setVerificationRequestId(verificationRequest.getId());
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(after);
        return walletTransactionRepository.save(transaction);
    }

    private CompanyBillingProfile getBillingProfileEntity(String companyId) {
        return billingProfileRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "Billing profile not found for company: " + companyId));
    }

    private Company getCompany(String companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Company not found: " + companyId));
    }

    private void validateTierExists(String tierCode) {
        if (!tiersConfig.tierExists(tierCode)) {
            throw new ApplicationExceptions.IntactBadRequestException("Unknown billing tier: " + tierCode);
        }
    }

    private String normalizeTierCode(String tierCode) {
        String value = tierCode.trim().toUpperCase().replace("-", "_");
        if (value.matches("TIER[123]")) {
            return value.replace("TIER", "TIER_");
        }
        return value;
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Boolean defaultTrue(Boolean value) {
        return value == null || value;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return defaultDecimal(value).setScale(4, RoundingMode.HALF_UP);
    }

    private String generateInvoiceNumber() {
        String month = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "INV-" + month + "-" + suffix;
    }

    private String resolveInvoiceRecipient(Company company, CompanyBillingProfile profile) {
        if (blankToNull(profile.getBillingEmail()) != null) {
            return profile.getBillingEmail().trim();
        }

        Optional<CompanyCommunicationDetails> optionalCommunication = companyCommunicationRepository.findByCompanyId(company.getId());
        if (optionalCommunication.isPresent()) {
            CompanyCommunicationDetails communication = optionalCommunication.get();
            if (blankToNull(communication.getBillingEmail()) != null) {
                return communication.getBillingEmail().trim();
            }
            if (blankToNull(communication.getEmail()) != null) {
                return communication.getEmail().trim();
            }
        }

        throw new ApplicationExceptions.InvalidActionException("No billing recipient email configured for company: " + company.getId());
    }

    private CompanyBillingProfileResponseDto toProfileResponse(CompanyBillingProfile profile) {
        return CompanyBillingProfileResponseDto.builder()
                .id(profile.getId())
                .companyId(profile.getCompany().getId())
                .billingTierCode(profile.getBillingTierCode())
                .billingMode(profile.getBillingMode())
                .unitPrice(profile.getUnitPrice())
                .currency(profile.getCurrency())
                .billingEmail(profile.getBillingEmail())
                .billOnSubmission(profile.getBillOnSubmission())
                .billOnCompleted(profile.getBillOnCompleted())
                .billOnRejected(profile.getBillOnRejected())
                .billOnFailedNonSystem(profile.getBillOnFailedNonSystem())
                .billOnFailedSystem(profile.getBillOnFailedSystem())
                .currentBalance(defaultDecimal(profile.getCurrentBalance()))
                .lowBalanceThreshold(defaultDecimal(profile.getLowBalanceThreshold()))
                .creditLimit(defaultDecimal(profile.getCreditLimit()))
                .invoiceDueDays(profile.getInvoiceDueDays())
                .status(profile.getStatus())
                .effectiveFrom(profile.getEffectiveFrom())
                .effectiveTo(profile.getEffectiveTo())
                .build();
    }

    private WalletTransactionResponseDto toWalletResponse(WalletTransaction transaction) {
        return WalletTransactionResponseDto.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .reference(transaction.getReference())
                .description(transaction.getDescription())
                .verificationRequestId(transaction.getVerificationRequestId())
                .balanceBefore(transaction.getBalanceBefore())
                .balanceAfter(transaction.getBalanceAfter())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private BillingInvoiceResponseDto toInvoiceResponse(BillingInvoice invoice) {
        List<BillingInvoiceLineResponseDto> lines = billingInvoiceLineRepository.findByInvoiceId(invoice.getId()).stream()
                .map(line -> BillingInvoiceLineResponseDto.builder()
                        .lineType(line.getLineType())
                        .description(line.getDescription())
                        .quantity(line.getQuantity())
                        .unitPrice(line.getUnitPrice())
                        .amount(line.getAmount())
                        .build())
                .toList();

        return BillingInvoiceResponseDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .companyId(invoice.getCompany().getId())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .billingMode(invoice.getBillingMode())
                .currency(invoice.getCurrency())
                .subtotal(invoice.getSubtotal())
                .tax(invoice.getTax())
                .total(invoice.getTotal())
                .amountPaid(invoice.getAmountPaid())
                .status(invoice.getStatus())
                .recipientEmail(invoice.getRecipientEmail())
                .dueAt(invoice.getDueAt())
                .generatedAt(invoice.getGeneratedAt())
                .sentAt(invoice.getSentAt())
                .lineItems(lines)
                .build();
    }

    private BigDecimal sumCharges(List<BillingCharge> charges, BillingChargeStatus status) {
        return charges.stream()
                .filter(charge -> charge.getStatus() == status)
                .map(BillingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
