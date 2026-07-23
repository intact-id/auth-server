package com.intact_id.db.repository.user;

import com.intact_id.db.model.user.BackupCode;
import com.intact_id.db.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupCodeRepository extends JpaRepository<BackupCode, String> {

    List<BackupCode> findByUserAndUsedAtIsNull(User user);

    long countByUserAndUsedAtIsNull(User user);

    void deleteByUser(User user);
}
