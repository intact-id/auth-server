package com.intact_id.db.repository.user;

import com.intact_id.db.model.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserId(String userId);

    void deleteByUserId(String userId);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    void deleteByRevokedTrue();
}
