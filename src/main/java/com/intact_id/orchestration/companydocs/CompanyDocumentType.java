package com.intact_id.orchestration.companydocs;

/** The compliance documents a company keeps with Intact ID. expires: whether the document has an expiry date. */
public enum CompanyDocumentType {
    CERTIFICATE_OF_INCORPORATION("Certificate of incorporation", "Registration", false),
    CR12("CR12: directors and shareholders", "Registration", true),
    PROOF_OF_ADDRESS("Proof of business address", "Registration", false),
    KRA_PIN("KRA PIN certificate", "Tax", false),
    TAX_COMPLIANCE("Tax compliance certificate", "Tax", true),
    AML_POLICY("AML/CFT policy", "Policy", false),
    BOARD_RESOLUTION("Board resolution: account opening", "Governance", false),
    BENEFICIAL_OWNERSHIP("Beneficial ownership declaration", "Governance", false),
    DIRECTORS_ID("Directors' identity documents", "Identity", false),
    DATA_PROTECTION_REGISTRATION("Data protection registration", "Compliance", true);

    private final String label;
    private final String category;
    private final boolean expires;

    CompanyDocumentType(String label, String category, boolean expires) {
        this.label = label;
        this.category = category;
        this.expires = expires;
    }

    public String label() { return label; }
    public String category() { return category; }
    public boolean expires() { return expires; }
}
