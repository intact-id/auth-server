package com.intact_id.orchestration;

import java.time.LocalDateTime;

/**
 * A director or owner was given a new verification link: when the business reached its owner step, on a resend,
 * or with a reminder. emailed says whether Intact emailed it; otherwise the merchant shares it themselves.
 */
public record PartyInviteCreated(String companyId, String businessVerificationId, String businessReference,
                                 String partyId, String firstName, String lastName, String email, String link,
                                 LocalDateTime expiresAt, boolean emailed, boolean reminder) {
}
