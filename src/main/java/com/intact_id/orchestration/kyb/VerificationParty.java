package com.intact_id.orchestration.kyb;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.intact_id.util.converter.PgJsonbToMapConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** A director or owner named on a business verification. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "verification_parties")
public class VerificationParty extends Auditable {

    // The business verification
    @Column(name = "verification_id", nullable = false)
    private String verificationId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "nationality", length = 3)
    private String nationality;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "is_director", nullable = false)
    private boolean director;

    @Column(name = "ownership_percent", precision = 5, scale = 2)
    private BigDecimal ownershipPercent;

    // Whether the settings at submission required this person to be checked
    @Column(name = "must_verify", nullable = false)
    private boolean mustVerify;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PartyStatus status;

    // Their own identity verification (FULL_KYC)
    @Column(name = "person_verification_id")
    private String personVerificationId;

    @Column(name = "screening", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> screening;

    @Column(name = "invite_token_hash", unique = true)
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private LocalDateTime inviteExpiresAt;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
