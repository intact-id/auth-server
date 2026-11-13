package com.intact_id.orchestration.kyb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationPartyRepository extends JpaRepository<VerificationParty, String> {

    List<VerificationParty> findByVerificationIdOrderByCreatedAtAsc(String verificationId);

    Optional<VerificationParty> findByPersonVerificationId(String personVerificationId);

    Optional<VerificationParty> findByInviteTokenHash(String inviteTokenHash);
}
