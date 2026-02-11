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
}
