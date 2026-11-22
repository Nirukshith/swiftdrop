package com.swiftdrop.auth.reset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailSender.class);
    private static final String DEFAULT_SENDER_EMAIL = "noreply@swiftdrop.com";

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public PasswordResetMailSender(
            JavaMailSender mailSender,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") String configuredUsername) {
        this.mailSender = mailSender;
        this.senderEmail = configuredUsername == null || configuredUsername.isBlank()
                ? DEFAULT_SENDER_EMAIL
                : configuredUsername;
    }

    public void sendResetEmail(String recipientEmail, String resetCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(recipientEmail);
            message.setSubject("Reset your SwiftDrop password");
            message.setText("""
                    Hello,

                    We received a request to reset the password for your SwiftDrop account.
                    Use the verification code below to continue:

                    %s

                    This code expires in 10 minutes.
                    For your security, do not share this code with anyone.
                    If you did not request a password reset, you can safely ignore this email.

                    Regards,
                    SwiftDrop Security Team
                    """.formatted(resetCode));

            mailSender.send(message);
            log.info("Password reset email successfully sent to recipient: {}", maskEmail(recipientEmail));
        } catch (Exception e) {
            // SMTP error messages often echo the recipient address, so keep them out of the default log level.
            log.error("Failed to send password reset email to {}: {}",
                    maskEmail(recipientEmail), e.getClass().getSimpleName());
            log.debug("Password reset email failure details", e);
        }
    }

    /**
     * Masks an email address for logging, e.g. {@code alice@example.com -> a***@example.com}.
     */
    static String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}

