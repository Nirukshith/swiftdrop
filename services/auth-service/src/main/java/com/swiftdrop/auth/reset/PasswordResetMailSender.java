package com.swiftdrop.auth.reset;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends password reset codes by email.
 *
 * <p>Delivery runs on a background thread after the surrounding transaction commits, so that
 * {@code POST /forgot} takes the same time for registered and unknown addresses (no timing-based
 * account enumeration) and a code is never emailed if saving its token fails.
 */
@Component
public class PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailSender.class);
    private static final String DEFAULT_SENDER_EMAIL = "noreply@swiftdrop.com";

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final Executor executor;

    public PasswordResetMailSender(
            JavaMailSender mailSender,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") String configuredUsername,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.mailSender = mailSender;
        this.senderEmail = configuredUsername == null || configuredUsername.isBlank()
                ? DEFAULT_SENDER_EMAIL
                : configuredUsername;
        this.executor = executor;
    }

    /**
     * Schedules the reset email. Inside a transaction it is sent after commit; otherwise immediately.
     * Either way the SMTP call runs on the executor, never on the request thread.
     */
    public void sendResetEmail(String recipientEmail, String resetCode) {
        Runnable delivery = () -> deliver(recipientEmail, resetCode);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(delivery, recipientEmail);
                }
            });
        } else {
            dispatch(delivery, recipientEmail);
        }
    }

    private void dispatch(Runnable delivery, String recipientEmail) {
        try {
            executor.execute(delivery);
        } catch (RejectedExecutionException e) {
            log.error("Could not schedule password reset email to {}: executor rejected the task",
                    maskEmail(recipientEmail));
        }
    }

    private void deliver(String recipientEmail, String resetCode) {
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
