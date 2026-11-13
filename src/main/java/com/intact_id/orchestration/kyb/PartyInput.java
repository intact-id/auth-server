package com.intact_id.orchestration.kyb;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A director or owner as submitted with a business verification. */
public record PartyInput(String firstName, String lastName, LocalDate dateOfBirth, String nationality, String email,
                         String phone, boolean director, BigDecimal ownershipPercent) {
}
