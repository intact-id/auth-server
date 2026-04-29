package com.intact_id.db.repository.billing;

import com.intact_id.db.model.billing.BillingInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingInvoiceLineRepository extends JpaRepository<BillingInvoiceLine, String> {
    List<BillingInvoiceLine> findByInvoiceId(String invoiceId);
}
