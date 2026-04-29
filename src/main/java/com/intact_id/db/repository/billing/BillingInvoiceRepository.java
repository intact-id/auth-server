package com.intact_id.db.repository.billing;

import com.intact_id.db.model.billing.BillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, String> {
    List<BillingInvoice> findByCompanyIdOrderByGeneratedAtDesc(String companyId);
}
