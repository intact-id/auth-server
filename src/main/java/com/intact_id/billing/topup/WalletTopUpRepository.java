package com.intact_id.billing.topup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

interface WalletTopUpRepository extends JpaRepository<WalletTopUp, String> {

    /** Newest first; companyId and statuses are optional filters (null = all). */
    @Query("SELECT t FROM WalletTopUp t WHERE (:companyId IS NULL OR t.companyId = :companyId)"
            + " AND (:anyStatus = true OR t.status IN :statuses) ORDER BY t.createdAt DESC")
    Page<WalletTopUp> search(@Param("companyId") String companyId, @Param("anyStatus") boolean anyStatus,
                             @Param("statuses") Collection<TopUpStatus> statuses, Pageable page);

    @Query("SELECT COUNT(t) > 0 FROM WalletTopUp t WHERE t.method = :method AND LOWER(t.reference) = LOWER(:reference)"
            + " AND t.status NOT IN (com.intact_id.billing.topup.TopUpStatus.REJECTED, com.intact_id.billing.topup.TopUpStatus.CANCELLED,"
            + " com.intact_id.billing.topup.TopUpStatus.FAILED)")
    boolean referenceInUse(@Param("method") TopUpMethod method, @Param("reference") String reference);

    long countByStatus(TopUpStatus status);
}
