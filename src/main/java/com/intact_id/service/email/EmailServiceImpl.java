package com.intact_id.service.email;

import com.intact_id.dto.email.EmailDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Email service implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.support-email}")
    private String supportEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.dashboard-url}")
    private String dashboardUrl;

    @Value("${app.docs-url}")
    private String docsUrl;

    private Map<String, Object> commonVars() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("supportEmail", supportEmail);
        vars.put("baseUrl", baseUrl);
        vars.put("dashboardUrl", dashboardUrl);
        vars.put("docsUrl", docsUrl);
        return vars;
    }

    @Override
    public boolean sendTemplatedEmail(EmailDTO emailDTO) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(emailDTO.getTo());
            helper.setSubject(emailDTO.getSubject());

            if (emailDTO.getCc() != null && emailDTO.getCc().length > 0) {
                helper.setCc(emailDTO.getCc());
            }

            if (emailDTO.getBcc() != null && emailDTO.getBcc().length > 0) {
                helper.setBcc(emailDTO.getBcc());
            }

            // Process template
            Context context = new Context();
            if (emailDTO.getVariables() != null) {
                context.setVariables(emailDTO.getVariables());
            }

            String htmlContent = templateEngine.process(emailDTO.getTemplateName(), context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", emailDTO.getTo());
            return true;

        } catch (MessagingException e) {
            log.error("Failed to build email to {}: {}", emailDTO.getTo(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Failed to send email to {} via SMTP: {}", emailDTO.getTo(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("Simple email sent successfully to: {}", to);
            return true;

        } catch (Exception e) {
            log.error("Failed to send simple email to: {}", to, e);
            return false;
        }
    }

    @Override
    public boolean sendCompanyApprovalEmail(String companyEmail, String companyName, String apiKey, String apiSecret) {
        Map<String, Object> variables = commonVars();
        variables.put("companyName", companyName);
        variables.put("apiKey", apiKey);
        variables.put("apiSecret", apiSecret);

        EmailDTO emailDTO = EmailDTO.builder()
                .to(companyEmail)
                .subject("Welcome to Intact ID - Your Application Has Been Approved!")
                .templateName("company-approved")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendCompanyCredentialsRegeneratedEmail(String companyEmail, String companyName,
            String apiKey, String apiSecret, String environment) {
        Map<String, Object> variables = commonVars();
        variables.put("companyName", companyName);
        variables.put("apiKey", apiKey);
        variables.put("apiSecret", apiSecret);
        variables.put("environment", environment);

        EmailDTO emailDTO = EmailDTO.builder()
                .to(companyEmail)
                .subject("Your " + environment + " API Credentials Have Been Regenerated")
                .templateName("company-credentials-regenerated")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendCompanyRejectionEmail(String companyEmail, String companyName, String rejectionReason) {
        Map<String, Object> variables = commonVars();
        variables.put("companyName", companyName);
        variables.put("rejectionReason", rejectionReason != null ? rejectionReason
                : "Your application did not meet our requirements at this time.");

        EmailDTO emailDTO = EmailDTO.builder()
                .to(companyEmail)
                .subject("Intact ID Application Status - Action Required")
                .templateName("company-rejected")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendCompanySuspensionEmail(String companyEmail, String companyName, String suspensionReason) {
        Map<String, Object> variables = commonVars();
        variables.put("companyName", companyName);
        variables.put("suspensionReason",
                suspensionReason != null ? suspensionReason : "Violation of terms of service");

        EmailDTO emailDTO = EmailDTO.builder()
                .to(companyEmail)
                .subject("Important: Your Intact ID Account Has Been Suspended")
                .templateName("company-suspended")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendCompanyActivationEmail(String companyEmail, String companyName) {
        Map<String, Object> variables = commonVars();
        variables.put("companyName", companyName);

        EmailDTO emailDTO = EmailDTO.builder()
                .to(companyEmail)
                .subject("Good News! Your Intact ID Account Has Been Reactivated")
                .templateName("company-activated")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendApplicationReceivedEmail(String companyEmail, String companyName,
            String applicationNumber, String trackUrl) {
        Map<String, Object> variables = commonVars();
        variables.put("companyName", companyName);
        variables.put("applicationNumber", applicationNumber);
        variables.put("trackUrl", trackUrl);

        EmailDTO emailDTO = EmailDTO.builder()
                .to(companyEmail)
                .subject("Application Received – " + applicationNumber)
                .templateName("application-received")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendApplicationUnderReviewEmail(String contactEmail, String contactPersonName,
            String companyName, String applicationNumber) {
        Map<String, Object> variables = commonVars();
        variables.put("contactPersonName", contactPersonName);
        variables.put("companyName", companyName);
        variables.put("applicationNumber", applicationNumber);

        EmailDTO emailDTO = EmailDTO.builder()
                .to(contactEmail)
                .subject("Your Application is Under Review – " + applicationNumber)
                .templateName("application-under-review")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendCheckerActionRequiredEmail(String checkerEmail, String checkerName,
            String companyName, String applicationNumber,
            String endUserMessage, String approverNote) {
        Map<String, Object> variables = commonVars();
        variables.put("checkerName", checkerName);
        variables.put("companyName", companyName);
        variables.put("applicationNumber", applicationNumber);
        variables.put("endUserMessage", endUserMessage);
        variables.put("approverNote", approverNote);

        EmailDTO emailDTO = EmailDTO.builder()
                .to(checkerEmail)
                .subject("Action Required: Application " + applicationNumber + " Pending Your Approval")
                .templateName("checker-action-required")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }

    @Override
    public boolean sendVerificationCompletedEmail(String applicantEmail, String applicantName,
            String verificationStatus, String verificationId) {
        Map<String, Object> variables = commonVars();
        variables.put("applicantName", applicantName);
        variables.put("verificationStatus", verificationStatus);
        variables.put("verificationId", verificationId);
        variables.put("isApproved", "APPROVED".equalsIgnoreCase(verificationStatus));

        String subject = "APPROVED".equalsIgnoreCase(verificationStatus)
                ? "Your Identity Verification Has Been Approved"
                : "Your Identity Verification Status Update";

        EmailDTO emailDTO = EmailDTO.builder()
                .to(applicantEmail)
                .subject(subject)
                .templateName("verification-completed")
                .variables(variables)
                .build();

        return sendTemplatedEmail(emailDTO);
    }
}
