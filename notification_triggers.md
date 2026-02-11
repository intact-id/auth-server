/
\f# Notification Triggers & Scenarios

This document outlines the system events that trigger automated email notifications in the Intact ID platform.

## 1. Company Application & Lifecycle

| Event | Trigger Condition | Recipient | Template | Variables |
|-------|-------------------|-----------|----------|-----------|
| **Application Approved** | Checker approves a company application in `CheckerApprovalService`. | Company Email | `company-approved.html` | `companyName`, `apiKey`, `apiSecret`, `dashboardUrl`, `docsUrl` |
| **Application Rejected** | Checker rejects a company application in `CheckerApprovalService`. | Company Email | `company-rejected.html` | `companyName`, `rejectionReason`, `supportEmail` |
| **Account Suspended** | Admin suspends a company account via `CompanyService.suspendCompany`. | Company Email | `company-suspended.html` | `companyName`, `suspensionReason`, `supportEmail` |
| **Account Reactivated** | Admin reactivates a suspended account via `CompanyService.activateCompany`. | Company Email | `company-activated.html` | `companyName`, `dashboardUrl` |
| **Account Deleted** | Admin deletes a company account via `CompanyService.deleteCompany`. | Company Email | Simple Email | Subject: "Account Deleted", Content: "Your account has been deleted." |

## 2. Identity Verification

| Event | Trigger Condition | Recipient | Template | Variables |
|-------|-------------------|-----------|----------|-----------|
| **Verification Completed** | Verification process finishes with a final status (Approved/Rejected) in `VerificationService`. | Applicant Email | `verification-completed.html` | `applicantName`, `verificationStatus`, `verificationId`, `isApproved` |

## 3. User Management (Planned)

| Event | Trigger Condition | Recipient | Template | Variables |
|-------|-------------------|-----------|----------|-----------|
| **Password Reset** | User requests password reset. | User Email | `password-reset.html` | `resetLink`, `expiryTime` |
| **Welcome Email** | New user registration. | User Email | `welcome.html` | `userName`, `loginUrl` |

## 4. System Alerts (Internal)

| Event | Trigger Condition | Recipient | Template | Variables |
|-------|-------------------|-----------|----------|-----------|
| **Critical Error** | System encounters a critical failure (e.g., payment gateway down). | Admin Email | Simple Email | Error details, timestamp |
