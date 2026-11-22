package com.swiftdrop.auth.reset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Shared rule G5: reset emails must not write the recipient's address to the logs unmasked.
 */
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetMailSenderTest {

    private static final String EMAIL = "alice.customer@example.com";

    @Test
    @DisplayName("Successful send logs a masked recipient")
    void send_success_logsMaskedRecipient(CapturedOutput output) {
        new PasswordResetMailSender(mock(JavaMailSender.class), "", Runnable::run).sendResetEmail(EMAIL, "123456");

        assertThat(output.getAll()).contains("a***@example.com").doesNotContain(EMAIL).doesNotContain("123456");
    }

    @Test
    @DisplayName("Failed send logs neither the recipient nor the SMTP error text that echoes it")
    void send_failure_logsNoRecipient(CapturedOutput output) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 mailbox unavailable: " + EMAIL))
                .when(mailSender).send(any(SimpleMailMessage.class));

        new PasswordResetMailSender(mailSender, "", Runnable::run).sendResetEmail(EMAIL, "123456");

        assertThat(output.getAll())
                .contains("a***@example.com")
                .contains("MailSendException")
                .doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("maskEmail handles malformed and missing addresses")
    void maskEmail_edgeCases() {
        assertThat(PasswordResetMailSender.maskEmail(null)).isEqualTo("***");
        assertThat(PasswordResetMailSender.maskEmail("no-at-sign")).isEqualTo("***");
        assertThat(PasswordResetMailSender.maskEmail("@example.com")).isEqualTo("***");
        assertThat(PasswordResetMailSender.maskEmail("bob@example.com")).isEqualTo("b***@example.com");
    }

    @Test
    @DisplayName("Delivery is handed to the executor instead of running on the caller's thread")
    void send_usesExecutor() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        java.util.List<Runnable> queued = new java.util.ArrayList<>();

        new PasswordResetMailSender(mailSender, "", queued::add).sendResetEmail(EMAIL, "123456");

        org.mockito.Mockito.verifyNoInteractions(mailSender);
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        org.mockito.Mockito.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Inside a transaction the email waits for commit, and is never sent on rollback")
    void send_waitsForCommit() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        PasswordResetMailSender sender = new PasswordResetMailSender(mailSender, "", Runnable::run);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            sender.sendResetEmail(EMAIL, "123456");
            org.mockito.Mockito.verifyNoInteractions(mailSender);

            // Simulate a rollback: synchronizations get afterCompletion, never afterCommit.
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            org.mockito.Mockito.verifyNoInteractions(mailSender);

            sender.sendResetEmail(EMAIL, "654321");
            var syncs = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            syncs.get(syncs.size() - 1).afterCommit();
            org.mockito.Mockito.verify(mailSender).send(any(SimpleMailMessage.class));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
