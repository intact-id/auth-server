package com.intact_id.billing.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    List<WalletTransaction> findByCompanyIdOrderByCreatedAtDesc(String companyId);
}
