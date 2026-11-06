package com.intact_id.billing.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingInvoiceLineRepository extends JpaRepository<BillingInvoiceLine, String> {
    List<BillingInvoiceLine> findByInvoiceId(String invoiceId);
}
