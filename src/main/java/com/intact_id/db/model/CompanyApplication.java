package com.intact_id.db.model;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.persistence.*;
import lombok.*;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 19:11
 */

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CompanyApplication extends Auditable {

    @Column(name = "application_number", unique = true, nullable = false, length = 50)
    private String applicationNumber;


    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trading_name")
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "email", nullable = false)
    private String email;


    @Column(name = "phone")
    private String phone;

    @Column(name = "website")
    private String website;

    @Column(name = "country", length = 2)
    private String country = "KE";

    @Column(name = "business_type")
    private String businessType; // digital_lender, neobank, crypto_exchange, remittance

    @Column(name = "application_status")
    @Enumerated(EnumType.STRING)
    private GlobalStatuses applicationStatus = GlobalStatuses.PENDING;

    @Column(name = "contact_person_name", length = 255)
    private String contactPersonName;

    @Column(name = "contact_person_title", length = 100)
    private String contactPersonTitle; // CTO, CEO, Product Manager

    @Column(name = "contact_person_email")
    private String contactPersonEmail;

    @Column(name = "contact_person_phone", length = 50)
    private String contactPersonPhone;


}
