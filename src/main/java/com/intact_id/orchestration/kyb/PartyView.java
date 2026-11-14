package com.intact_id.orchestration.kyb;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** A director or owner as the API and console show them. verificationId is their own verification (FULL_KYC). */
public record PartyView(String id, String firstName, String lastName, String email, boolean director,
                        BigDecimal ownershipPercent, boolean required, String status, String verificationId,
                        Map<String, Object> screening, LocalDateTime invitedAt, LocalDateTime inviteExpiresAt,
                        boolean inviteEmailed, int remindersSent) {

    public static PartyView of(VerificationParty p) {
        return new PartyView(p.getId(), p.getFirstName(), p.getLastName(), p.getEmail(), p.isDirector(),
                p.getOwnershipPercent(), p.isMustVerify(), p.getStatus().name(), p.getPersonVerificationId(),
                p.getScreening(), p.getInvitedAt(), p.getInviteExpiresAt(), p.isInviteEmailed(), p.getRemindersSent());
    }
}
