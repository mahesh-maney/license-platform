package com.modus.license.notification.service;

import com.modus.license.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Dispatches email notifications via Spring Mail (SMTP / Office 365).
 */
@Component
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties props;

    public EmailDispatchService(JavaMailSender mailSender, NotificationProperties props) {
        this.mailSender = mailSender;
        this.props      = props;
    }

    /**
     * Sends a plain-text email.
     *
     * @throws org.springframework.mail.MailException on delivery failure
     */
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.email().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.debug("Email sent to={} subject={}", to, subject);
    }
}
