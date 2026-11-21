package com.swiftdrop.integration.merchant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MerchantApprovalMailSender {

    private static final Logger log = LoggerFactory.getLogger(MerchantApprovalMailSender.class);
    private static final String DEFAULT_SENDER_EMAIL = "noreply@swiftdrop.com";

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public MerchantApprovalMailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String configuredUsername) {
        this.mailSender = mailSender;
        this.senderEmail = configuredUsername == null || configuredUsername.isBlank()
                ? DEFAULT_SENDER_EMAIL
                : configuredUsername;
    }

    public void sendApprovalEmail(String recipientEmail, String businessName, String temporaryPassword) {
        if (temporaryPassword == null || !temporaryPassword.matches("^[A-Za-z0-9]{6}$")) {
            log.error("Merchant approval succeeded but no valid temporary password was available for approval email");
            return;
        }

        log.info("Merchant temporary credentials generated for '{}': Email={}, Temporary Password={}",
                businessName, recipientEmail, temporaryPassword);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("Your SwiftDrop merchant account is approved");
        message.setText("""
                Hello %s,

                Your SwiftDrop merchant registration has been approved.
                Sign in to the SwiftDrop Merchant Portal with these temporary credentials:

                Email: %s
                Temporary password: %s

                You will be asked to create a new password after your first sign-in.

                If you did not submit this registration, please contact SwiftDrop support.

                Regards,
                SwiftDrop Merchant Support
                """.formatted(businessName, recipientEmail, temporaryPassword));

        try {
            mailSender.send(message);
            log.info("Merchant approval email successfully sent to recipient: {}", recipientEmail);
        } catch (Exception exception) {
            log.error("Merchant approval succeeded but approval email could not be sent to {}: {}",
                    recipientEmail, exception.getMessage());
            // Fallback to local Mailpit (localhost:1025) if external SMTP is rate-limited or fails
            try {
                org.springframework.mail.javamail.JavaMailSenderImpl fallback = new org.springframework.mail.javamail.JavaMailSenderImpl();
                fallback.setHost("localhost");
                fallback.setPort(1025);
                fallback.send(message);
                log.info("Merchant approval email successfully delivered to local fallback (Mailpit) for: {}", recipientEmail);
            } catch (Exception fallbackEx) {
                log.warn("Local Mailpit fallback sending also failed: {}", fallbackEx.getMessage());
            }
        }
    }

    public void sendRejectionEmail(String recipientEmail, String businessName, String rejectionReason) {
        log.info("Sending merchant rejection email for '{}' to {}", businessName, recipientEmail);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("Your SwiftDrop merchant registration has been rejected");
        message.setText("""
                Hello %s,

                Thank you for your interest in SwiftDrop.
                Your merchant registration has been reviewed and rejected by the administrator.

                Reason: %s

                If you believe this was a mistake or have questions, please contact SwiftDrop support.

                Regards,
                SwiftDrop Merchant Support
                """.formatted(businessName, rejectionReason != null && !rejectionReason.isBlank() ? rejectionReason.trim() : "Application requirements not met"));

        try {
            mailSender.send(message);
            log.info("Merchant rejection email successfully sent to recipient: {}", recipientEmail);
        } catch (Exception exception) {
            log.error("Merchant rejection email could not be sent to {}: {}",
                    recipientEmail, exception.getMessage());
            // Fallback to local Mailpit (localhost:1025) if external SMTP is rate-limited or fails
            try {
                org.springframework.mail.javamail.JavaMailSenderImpl fallback = new org.springframework.mail.javamail.JavaMailSenderImpl();
                fallback.setHost("localhost");
                fallback.setPort(1025);
                fallback.send(message);
                log.info("Merchant rejection email successfully delivered to local fallback (Mailpit) for: {}", recipientEmail);
            } catch (Exception fallbackEx) {
                log.warn("Local Mailpit fallback sending also failed: {}", fallbackEx.getMessage());
            }
        }
    }

    public void sendCredentialsEmail(String recipientEmail, String businessName, String clientId, String clientSecret) {
        log.info("Sending merchant credentials email for '{}' to {}", businessName, recipientEmail);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("Your SwiftDrop API Credentials & Integration Details");
        message.setText("""
                Hello %s,

                Your SwiftDrop merchant API credentials and integration specifications are ready.

                --------------------------------------------------
                API CREDENTIALS
                --------------------------------------------------
                Client ID: %s
                Client Secret: %s

                * Keep this Client Secret confidential and secure. Do not share it publicly.

                --------------------------------------------------
                TOKEN REQUEST & INTEGRATION SPECIFICATIONS
                --------------------------------------------------
                Protocol: OAuth 2.0
                Grant Type: client_credentials
                Base API URL: /api/v1/merchant
                Rate Limit: 60 requests / min

                Request Header Format:
                Authorization: Bearer <access-token>

                How to use:
                1. Request a short-lived bearer token using the client credentials grant with your Client ID and Client Secret.
                2. Send the obtained token in the 'Authorization: Bearer <access-token>' header when calling merchant APIs.

                If you have any questions or require developer assistance, please contact SwiftDrop Support.

                Regards,
                SwiftDrop Integration Team
                """.formatted(businessName, clientId, clientSecret != null ? clientSecret : "[Previously dispatched]"));

        try {
            mailSender.send(message);
            log.info("Merchant credentials email successfully sent to recipient: {}", recipientEmail);
        } catch (Exception exception) {
            log.error("Merchant credentials email could not be sent to {}: {}",
                    recipientEmail, exception.getMessage());
            // Fallback to local Mailpit (localhost:1025) if external SMTP is rate-limited or fails
            try {
                org.springframework.mail.javamail.JavaMailSenderImpl fallback = new org.springframework.mail.javamail.JavaMailSenderImpl();
                fallback.setHost("localhost");
                fallback.setPort(1025);
                fallback.send(message);
                log.info("Merchant credentials email successfully delivered to local fallback (Mailpit) for: {}", recipientEmail);
            } catch (Exception fallbackEx) {
                log.warn("Local Mailpit fallback sending also failed: {}", fallbackEx.getMessage());
            }
        }
    }
}
