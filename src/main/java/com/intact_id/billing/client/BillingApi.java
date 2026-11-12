package com.intact_id.billing.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** billing-service's accounts, wallets, prices and invoices. */
public final class BillingApi {

    private BillingApi() {
    }

    public enum ClientType { SERVICE, ENTITY }

    public enum BillingModel { PER_CALL, FLAT_PERIODIC, HYBRID }

    public enum PaymentType { PREPAID, POSTPAID }

    public enum WalletTransactionType { CREDIT, DEBIT }

    public enum InvoiceStatus { ISSUED, PAID, VOID }

    // --- Responses ---

    public record Client(String id, String clientIdentifier, ClientType clientType, String clientName,
                         String description, Boolean isActive) {
    }

    public record BillingProfile(String id, String clientId, BillingModel billingModel, String currency,
                                 PaymentType paymentType, Boolean isActive) {
    }

    public record Wallet(String id, String billingProfileId, BigDecimal balance, String currency, Boolean isActive) {
    }

    /** workflowId null = the company's default price per call. */
    public record PerCallPricing(String id, String billingProfileId, BigDecimal pricePerCall, Long freeQuota,
                                 Boolean isActive, String workflowId) {
    }

    /** Parts not set up yet are null. */
    public record Account(Client client, BillingProfile billingProfile, Wallet wallet, PerCallPricing perCallPricing) {
    }

    public record WalletTransaction(String id, String walletId, WalletTransactionType type, BigDecimal amount,
                                    BigDecimal balanceAfter, String externalRef, String description,
                                    LocalDateTime createdAt) {
    }

    public record Invoice(String id, String billingProfileId, String recurringFeeId, BigDecimal amount, String currency,
                          LocalDateTime periodStart, LocalDateTime periodEnd, InvoiceStatus status) {
    }

    /** billingProfileId null = a global price. */
    public record ServiceCharge(String id, String serviceCode, String name, String billingProfileId, BigDecimal price,
                                String currency, Boolean isActive) {
    }

    // --- Requests from the dashboard ---

    /** Terms for a company's billing profile; pricePerCall (optional) sets its default price per call. */
    public record Setup(@NotNull BillingModel billingModel, @NotNull PaymentType paymentType,
                        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
                        @PositiveOrZero BigDecimal pricePerCall, @PositiveOrZero Long freeQuota) {
    }

    /** Only the fields sent are changed. */
    public record ProfileChanges(BillingModel billingModel, PaymentType paymentType,
                                 @Pattern(regexp = "^[A-Z]{3}$") String currency, Boolean isActive) {
    }

    public record WalletCredit(@NotNull @Positive BigDecimal amount, String externalRef, String description) {
    }

    /** Only the fields sent are changed. */
    public record PerCallChanges(@PositiveOrZero BigDecimal pricePerCall, @PositiveOrZero Long freeQuota, Boolean isActive) {
    }

    /** serviceCode is the workflow catalog code, e.g. LIVENESS. currency is required for a global price; a company's own price is always in its billing currency. */
    public record NewServiceCharge(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$") String serviceCode,
                                   String name, @NotNull @PositiveOrZero BigDecimal price,
                                   @Pattern(regexp = "^[A-Z]{3}$") String currency) {
    }

    /** Only the fields sent are changed. */
    public record ServiceChargeChanges(String name, @PositiveOrZero BigDecimal price, Boolean isActive) {
    }

    // --- Bodies billing-service takes ---

    public record ClientCreation(String clientIdentifier, ClientType clientType, String clientName, String description,
                                 Map<String, Object> extraDetails) {
    }

    public record BillingProfileCreation(String clientId, BillingModel billingModel, String currency,
                                         PaymentType paymentType) {
    }

    public record WalletCreation(String billingProfileId) {
    }

    public record PerCallPricingCreation(String billingProfileId, BigDecimal pricePerCall, Long freeQuota) {
    }

    public record ServiceChargeCreation(String serviceCode, String name, String billingProfileId, BigDecimal price,
                                        String currency) {
    }
}
