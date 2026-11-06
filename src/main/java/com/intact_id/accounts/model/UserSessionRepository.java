package com.intact_id.accounts.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String>, JpaSpecificationExecutor<UserSession> {

    Optional<UserSession> findBySessionId(String sessionId);
}
