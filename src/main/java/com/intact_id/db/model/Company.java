package com.intact_id.db.model;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.util.converter.PgJsonbToMapConverter;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 16:31
 */

@Entity
@Getter
@Setter
@Table(name = "company_tbl")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Company extends Auditable {


    @Column(name = "company_identifier_code",unique = true, nullable = false)
    private String companyIdentifierCode;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trading_name")
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "country", length = 2)
    private String country ;

    @Column(name = "business_type")
    private String businessType; // digital_lender, neobank, crypto_exchange

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private GlobalStatuses status = GlobalStatuses.PENDING_APPROVAL;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy; // References admins.id

    @Column(name = "application_id")
    private String applicationId;

    @OneToOne(mappedBy = "company", cascade = CascadeType.ALL)
    private CompanyCommunicationDetails communication;

    @OneToOne(mappedBy = "company", cascade = CascadeType.ALL)
    private CompanyCredentials credentials;

    @Column(name = "extra_details",columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = PgJsonbToMapConverter.class)
    private Map<String,Object> metaData=new HashMap<>();

    public boolean isActive() {
        return this.status == GlobalStatuses.ACTIVE;
    }

    public void activate(String approvedBy) {
        this.status = GlobalStatuses.ACTIVE;
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approvedBy;
    }

    public void suspend() {
        this.status = GlobalStatuses.SUSPENDED;
    }

    public void close() {
        this.status = GlobalStatuses.CLOSED;
    }

}
