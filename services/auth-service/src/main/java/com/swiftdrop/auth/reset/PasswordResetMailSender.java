package com.swiftdrop.auth.reset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailSender.class);

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;

    public PasswordResetMailSender(
            JavaMailSender mailSender,
            @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public void sendResetEmail(String recipientEmail, String rawToken) {
        try {
            String resetUrl = frontendBaseUrl + "/reset-password?token=" + rawToken;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@swiftdrop.com");
            message.setTo(recipientEmail);
            message.setSubject("Reset your SwiftDrop password");
            message.setText("""
                    Hello,

                    We received a request to reset your password for your SwiftDrop account.
                    Click the link below to set a new password:

                    %s

                    This link will expire in 30 minutes. If you did not request a password reset, please ignore this email.

                    Regards,
                    The SwiftDrop Team
                    """.formatted(resetUrl));

            mailSender.send(message);
            log.info("Password reset email sent to recipient");
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
        }
    }
}

