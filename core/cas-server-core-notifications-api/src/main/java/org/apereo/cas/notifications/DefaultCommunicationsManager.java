package org.apereo.cas.notifications;

import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.notifications.mail.EmailCommunicationResult;
import org.apereo.cas.notifications.mail.EmailMessageRequest;
import org.apereo.cas.notifications.mail.EmailSender;
import org.apereo.cas.notifications.push.NotificationSender;
import org.apereo.cas.notifications.sms.SmsRequest;
import org.apereo.cas.notifications.sms.SmsSender;
import org.apereo.cas.util.function.FunctionUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jooq.lambda.Unchecked;

import java.util.Map;
import java.util.Objects;

/**
 * This is {@link DefaultCommunicationsManager}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultCommunicationsManager implements CommunicationsManager {
    private final SmsSender smsSender;

    private final EmailSender emailSender;

    private final NotificationSender notificationSender;

    @Override
    public boolean isMailSenderDefined() {
        return this.emailSender != null && this.emailSender.canSend();
    }

    @Override
    public boolean isSmsSenderDefined() {
        return this.smsSender != null && this.smsSender.canSend();
    }

    @Override
    public boolean isNotificationSenderDefined() {
        return this.notificationSender != null && this.notificationSender.canSend();
    }

    @Override
    public boolean notify(final Principal principal, final String title, final String body) {
        return this.notificationSender.notify(principal, Map.of("title", title, "message", body));
    }

    @Override
    public EmailCommunicationResult email(final EmailMessageRequest emailRequest) {
        val recipients = Objects.requireNonNull(emailRequest.getRecipients(), "Email recipients cannot be undefined");
        LOGGER.trace("Attempting to send email [{}] to [{}]", emailRequest.getBody(), recipients);
        return FunctionUtils.doIf(isMailSenderDefined() && emailRequest.getEmailProperties().isDefined() && !recipients.isEmpty(),
            Unchecked.supplier(() -> emailSender.send(emailRequest)),
            () -> EmailCommunicationResult.builder().success(false)
                .to(recipients).body(emailRequest.getBody()).build()).get();
    }

    @Override
    public boolean sms(final SmsRequest smsRequest) {
        val recipient = smsRequest.getRecipient();
        if (!isSmsSenderDefined() || !smsRequest.isSufficient()) {
            LOGGER.warn("Could not send SMS to [{}]; No from/text is found or SMS settings are undefined.", recipient);
            return false;
        }
        return smsSender.send(smsRequest.getFrom(), recipient, smsRequest.getText());
    }

    @Override
    public boolean validate() {
        if (!isMailSenderDefined()) {
            LOGGER.info("CAS will not send emails because settings are undefined to account for email servers");
        }
        if (!isSmsSenderDefined()) {
            LOGGER.info("CAS will not send sms messages because settings are undefined to account for sms providers");
        }
        if (!isNotificationSenderDefined()) {
            LOGGER.info("CAS will not send notifications because providers are undefined to handle messages");
        }
        return isMailSenderDefined() || isSmsSenderDefined() || isNotificationSenderDefined();
    }

}
