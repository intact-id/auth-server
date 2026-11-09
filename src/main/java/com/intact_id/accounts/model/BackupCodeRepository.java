package com.intact_id.accounts.model;

import com.intact_id.accounts.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupCodeRepository extends JpaRepository<BackupCode, String> {

    List<BackupCode> findByUserAndUsedAtIsNull(User user);

    long countByUserAndUsedAtIsNull(User user);

    void deleteByUser(User user);
}
