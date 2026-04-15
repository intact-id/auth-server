package com.intact_id.db.repository.user;

import com.intact_id.db.model.user.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, String>, JpaSpecificationExecutor<UserActivityLog> {

    long countBySession_SessionId(String sessionId);
}
