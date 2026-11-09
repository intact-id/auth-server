package com.intact_id.billing.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, String> {
    List<BillingInvoice> findByCompanyIdOrderByGeneratedAtDesc(String companyId);
}
