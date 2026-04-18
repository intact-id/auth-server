package com.intact_id.service.email;

import com.intact_id.dto.email.EmailDTO;

/**
 * Service interface for sending emails
 */
public interface EmailService {

    /**
     * Send a templated email
     * 
     * @param emailDTO Email data including template and variables
     * @return true if sent successfully, false otherwise
     */
    boolean sendTemplatedEmail(EmailDTO emailDTO);

    /**
     * Send a simple text email
     * 
     * @param to      Recipient email
     * @param subject Email subject
     * @param content Email content
     * @return true if sent successfully, false otherwise
     */
    boolean sendSimpleEmail(String to, String subject, String content);

    /**
     * Send company approval email with credentials
     * 
     * @param companyEmail Company email address
     * @param companyName  Company name
     * @param apiKey       Generated API key
     * @param apiSecret    Generated API secret
     * @return true if sent successfully
     */
    boolean sendCompanyApprovalEmail(String companyEmail, String companyName, String apiKey, String apiSecret);

    /**
     * Send company email after API credentials are regenerated.
     *
     * @param companyEmail Company email address
     * @param companyName  Company name
     * @param apiKey       Regenerated API key
     * @param apiSecret    Regenerated API secret
     * @param environment  Credential environment (DEV/PROD)
     * @return true if sent successfully
     */
    boolean sendCompanyCredentialsRegeneratedEmail(String companyEmail, String companyName,
            String apiKey, String apiSecret, String environment);

    /**
     * Send company rejection email
     * 
     * @param companyEmail    Company email address
     * @param companyName     Company name
     * @param rejectionReason Reason for rejection
     * @return true if sent successfully
     */
    boolean sendCompanyRejectionEmail(String companyEmail, String companyName, String rejectionReason);

    /**
     * Send company suspension email
     * 
     * @param companyEmail     Company email address
     * @param companyName      Company name
     * @param suspensionReason Reason for suspension
     * @return true if sent successfully
     */
    boolean sendCompanySuspensionEmail(String companyEmail, String companyName, String suspensionReason);

    /**
     * Send company activation email
     * 
     * @param companyEmail Company email address
     * @param companyName  Company name
     * @return true if sent successfully
     */
    boolean sendCompanyActivationEmail(String companyEmail, String companyName);

    /**
     * Send verification completed email
     *
     * @param applicantEmail     Applicant email
     * @param applicantName      Applicant name
     * @param verificationStatus Status (APPROVED, REJECTED, etc.)
     * @param verificationId     Verification ID
     * @return true if sent successfully
     */
    boolean sendVerificationCompletedEmail(String applicantEmail, String applicantName,
            String verificationStatus, String verificationId);

    /**
     * Send applicant KYC result email with company context and optional rejection reason.
     *
     * @param applicantEmail   Applicant email address
     * @param applicantName    Applicant full name
     * @param companyName      Name of the company that requested the verification
     * @param decision         "approved", "rejected", or "pending_review"
     * @param rejectionReason  Human-readable rejection reason (null for approvals)
     * @param verificationId   Verification request ID
     * @return true if sent successfully
     */
    boolean sendApplicantResultEmail(String applicantEmail, String applicantName,
            String companyName, String decision, String rejectionReason, String verificationId);

    /**
     * Send application received confirmation email
     *
     * @param companyEmail      Company email address
     * @param companyName       Company legal name
     * @param applicationNumber Generated application number (e.g. APP-2026-XXXXXXXX)
     * @param trackUrl          Public URL where applicant can track their status
     * @return true if sent successfully
     */
    boolean sendApplicationReceivedEmail(String companyEmail, String companyName,
            String applicationNumber, String trackUrl);

    /**
     * Notify the application's contact person that their application is under review (maker approved).
     * Does NOT include any internal notes or end-user messages.
     */
    boolean sendApplicationUnderReviewEmail(String contactEmail, String contactPersonName,
            String companyName, String applicationNumber);

    /**
     * Alert a checker/admin that an application is pending their approval.
     * Includes the maker's end-user message and approver note.
     */
    boolean sendCheckerActionRequiredEmail(String checkerEmail, String checkerName,
            String companyName, String applicationNumber,
            String endUserMessage, String approverNote);
}
