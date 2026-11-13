# Notification System Integration Guide

## Overview
The notification system now supports both email delivery and database persistence. All notifications are stored in the database and can optionally trigger email notifications.

## Two Methods for Creating Notifications

### 1. `createNotification` - With Email
Use this when creating notifications from the dashboard. It will:
- Save the notification to the database
- Send an email to the company
- Return the notification details

```java
@Autowired
private NotificationService notificationService;

// Create notification with email
NotificationRequestDto request = new NotificationRequestDto();
request.setCompanyId("company-uuid");
request.setTitle("Important Update");
request.setMessage("Your account has been updated");
request.setType("INFO"); // INFO, SUCCESS, WARNING, ERROR

NotificationResponseDto notification = notificationService.createNotification(request);
```

### 2. `persistNotification` - Without Email
Use this for system-generated notifications (approvals, suspensions, etc.) where you want to persist the notification but handle email separately:

```java
@Autowired
private NotificationService notificationService;

// Persist notification without email
notificationService.persistNotification(
    companyId,
    "Application Approved",
    "Your application has been approved and credentials have been generated.",
    "SUCCESS"
);
```

## Integration Examples

### Company Approval Service
```java
// In CompanyApplicationServiceImpl or CheckerApprovalService
@Autowired
private NotificationService notificationService;

public void approveApplication(String applicationId) {
    // ... existing approval logic ...
    
    // Persist notification (email already sent by approval service)
    notificationService.persistNotification(
        company.getId(),
        "Application Approved",
        "Your company application has been approved. API credentials have been sent to your email.",
        "SUCCESS"
    );
}
```

### Company Suspension
```java
// In CompanyServiceImpl
public void suspendCompany(String companyId, String reason) {
    // ... existing suspension logic ...
    
    // Persist notification (email already sent by suspension service)
    notificationService.persistNotification(
        companyId,
        "Account Suspended",
        "Your account has been suspended. Reason: " + reason,
        "WARNING"
    );
}
```

### Verification Completed
```java
// In VerificationService or WebhookService
public void handleVerificationCompleted(String companyId, String applicantName, String status) {
    // ... existing webhook logic ...
    
    // Persist notification
    notificationService.persistNotification(
        companyId,
        "Verification Completed",
        String.format("Verification for %s completed with status: %s", applicantName, status),
        status.equals("APPROVED") ? "SUCCESS" : "INFO"
    );
}
```

## Notification Types
- **INFO** - General information
- **SUCCESS** - Success messages (approvals, completions)
- **WARNING** - Warnings (suspensions, issues)
- **ERROR** - Error messages (failures, rejections)

## Email Format
When `createNotification` is called, emails are sent with the following format:

```
Dear [Company Name],

[Notification Message]

Notification Type: [TYPE]
Date: [Timestamp]

Best regards,
Intact ID Team
```

## Inbox (the bell)
Every signed-in person has an inbox:
- Company users (`ADMIN`, `COMPANY`) see their own company's notifications.
- Super admins see the **staff inbox**: notifications with no company. The system adds them through
  `NotificationService.notifyStaff(title, message, type, link)` when a company applies, when an application
  passes the first review, when a verification goes to manual review, and when a service goes down or comes back.

| Endpoint | What it does |
|---|---|
| `GET /api/notifications/inbox?page&size` | The caller's inbox, newest first |
| `GET /api/notifications/inbox/unread-count` | Unread count for the bell |
| `PUT /api/notifications/inbox/read` | Marks the whole inbox read; returns how many changed |
| `PUT /api/notifications/{id}/read` | Marks one read (only within the caller's inbox, except super admins) |
| `GET /api/notifications` | Super admins: what was sent to companies. Company admins: their company's |

`notifyStaff` saves in its own transaction and never throws, so a failed notification never undoes the work
that triggered it.

## Database Schema
Notifications are stored in the `notifications` table:
- `id` - Notification ID
- `company_id` - The company it is for; null = Intact staff inbox
- `title`, `message` - What it says
- `link` - Console page it is about (e.g. `/dashboard/applications`), nullable
- `type` - INFO, SUCCESS, WARNING, ERROR
- `status` - UNREAD, READ
- `created_at`, `read_at`

## Best Practices

1. **Use `persistNotification` for system events** where email is already handled by the specific service
2. **Use `createNotification` for manual notifications** from the dashboard
3. **Always provide meaningful titles and messages**
4. **Use appropriate notification types** to help users prioritize
5. **Don't duplicate emails** - if your service already sends an email, use `persistNotification`

## Next Steps for Full Integration

To integrate notifications into existing services:

1. **Inject NotificationService** into your service class
2. **Call `persistNotification`** after sending emails in:
   - Company approval/rejection
   - Company suspension/activation
   - Verification completions
   - API key generation
   - Any other email-sending operations

Example:
```java
@Service
@RequiredArgsConstructor
public class YourService {
    private final NotificationService notificationService;
    private final EmailService emailService;
    
    public void someOperation(String companyId) {
        // Do your operation
        emailService.sendEmail(...);
        
        // Persist notification for dashboard
        notificationService.persistNotification(
            companyId,
            "Operation Completed",
            "Your operation was successful",
            "SUCCESS"
        );
    }
}
```
