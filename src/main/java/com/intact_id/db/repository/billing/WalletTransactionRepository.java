package com.intact_id.db.repository.billing;

import com.intact_id.db.model.billing.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    List<WalletTransaction> findByCompanyIdOrderByCreatedAtDesc(String companyId);
}
