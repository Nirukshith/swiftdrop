package com.swiftdrop.integration.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);
    private static final String DEFAULT_SENDER = "no-reply@swiftdrop.com";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailChannel(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public String getChannelType() {
        return "EMAIL";
    }

    @Override
    public void send(NotificationMessage message) {
        if (message.recipientTarget() == null || message.recipientTarget().isBlank()) {
            log.warn("Cannot send email: recipientTarget is empty for notification {}", message.id());
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured in this environment. Skipping email dispatch for notification {}", message.id());
            return;
        }

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(DEFAULT_SENDER);
        mailMessage.setTo(message.recipientTarget());
        mailMessage.setSubject(message.title());
        mailMessage.setText(message.content());

        log.info("Dispatching email notification to {} with subject '{}'", message.recipientTarget(), message.title());
        mailSender.send(mailMessage);
    }
}

