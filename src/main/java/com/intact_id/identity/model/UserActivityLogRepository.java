package com.intact_id.identity.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, String>, JpaSpecificationExecutor<UserActivityLog> {

    long countBySession_SessionId(String sessionId);
}
