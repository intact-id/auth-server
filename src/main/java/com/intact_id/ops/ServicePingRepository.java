package com.intact_id.ops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

interface ServicePingRepository extends JpaRepository<ServicePing, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM ServicePing p WHERE p.checkedAt < :before")
    int deleteOlderThan(LocalDateTime before);
}
