package com.intact_id.accounts;

/** A company was approved and created; other modules set up what it needs (billing account, screening tenant). */
public record CompanyCreated(String companyId, String companyCode, String legalName, String country) {
}
