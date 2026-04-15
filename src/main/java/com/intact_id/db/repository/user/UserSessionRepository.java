package com.intact_id.db.repository.user;

import com.intact_id.db.model.user.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String>, JpaSpecificationExecutor<UserSession> {

    Optional<UserSession> findBySessionId(String sessionId);
}
