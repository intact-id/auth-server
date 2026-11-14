package com.intact_id.orchestration.kyb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationPartyRepository extends JpaRepository<VerificationParty, String> {

    List<VerificationParty> findByVerificationIdOrderByCreatedAtAsc(String verificationId);

    Optional<VerificationParty> findByPersonVerificationId(String personVerificationId);

    Optional<VerificationParty> findByInviteTokenHash(String inviteTokenHash);

    /** People Intact emailed a link to who haven't started, while their link still works: reminder candidates. */
    List<VerificationParty> findByStatusAndInviteEmailedTrueAndInviteExpiresAtAfter(PartyStatus status, LocalDateTime now);
}
