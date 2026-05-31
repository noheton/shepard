package de.dlr.shepard.v2.notifications.transport.senders;

import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationMessage;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationSender;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * NTF1-BACKEND-SMTP — Jakarta Mail-based SMTP sender.
 *
 * <p>Establishes a fresh {@link Session} + {@link Transport} per send.
 * This is intentional: operator-configured {@code :NotificationTransport}
 * rows are not the deploy-time fixed SMTP relay that {@code quarkus-mailer}
 * is built around, so we can't reuse a Quarkus-managed connection. The
 * cost is a TCP handshake per notification, which is acceptable for
 * admin smoke-tests + low-volume event notifications.
 *
 * <p><b>STARTTLS posture.</b> When {@link NotificationTransport#getSmtpTls()}
 * is {@code true}, sets {@code mail.smtp.starttls.enable=true} and
 * {@code mail.smtp.starttls.required=true}. {@code null} or {@code false}
 * leaves the connection plain. Servers expecting implicit TLS (port 465)
 * are not supported in v1 — operators should use STARTTLS on 587.
 *
 * <p><b>Testability.</b> {@link #doSend(Session, MimeMessage)} is the
 * package-private seam tests override to avoid hitting the network.
 * Production calls go through this method; the default impl calls
 * {@link Transport#send}.
 */
@ApplicationScoped
public class SmtpNotificationSender implements NotificationSender {

  @Override
  public TransportKind kind() {
    return TransportKind.SMTP;
  }

  @Override
  public boolean send(NotificationTransport transport, NotificationMessage message) {
    if (transport == null || message == null) {
      throw new IllegalArgumentException("transport and message must not be null");
    }
    if (transport.getSmtpHost() == null || transport.getSmtpHost().isBlank()
        || transport.getSmtpFrom() == null || transport.getSmtpFrom().isBlank()) {
      Log.warnf("NTF1-SMTP: transport appId=%s missing smtpHost or smtpFrom — cannot send",
          transport.getAppId());
      return false;
    }
    String recipient = message.recipient();
    if (recipient == null || recipient.isBlank()) {
      Log.warnf("NTF1-SMTP: message has no recipient and SMTP has no fallback — skipping send for transport appId=%s",
          transport.getAppId());
      return false;
    }

    try {
      Session session = buildSession(transport);
      MimeMessage msg = new MimeMessage(session);
      msg.setFrom(new InternetAddress(transport.getSmtpFrom()));
      msg.setRecipients(Message.RecipientType.TO, parseAddresses(recipient));
      msg.setSubject(message.title() == null ? "(no subject)" : message.title());
      String body = message.body() == null ? "" : message.body();
      if (message.actionUrl() != null && !message.actionUrl().isBlank()) {
        body = body + "\n\n" + message.actionUrl();
      }
      msg.setText(body, "UTF-8");

      doSend(session, msg);
      Log.debugf("NTF1-SMTP: delivered notification to %s via %s",
          recipient, transport.getSmtpHost());
      return true;
    } catch (MessagingException e) {
      Log.warnf(e, "NTF1-SMTP: send failed for transport appId=%s", transport.getAppId());
      return false;
    }
  }

  /**
   * Build a {@link Session} configured per the transport row. Package-
   * private to permit visibility from tests in the same package.
   */
  Session buildSession(NotificationTransport t) {
    Properties props = new Properties();
    props.put("mail.smtp.host", t.getSmtpHost());
    if (t.getSmtpPort() != null && t.getSmtpPort() > 0) {
      props.put("mail.smtp.port", String.valueOf(t.getSmtpPort()));
    }
    boolean tls = Boolean.TRUE.equals(t.getSmtpTls());
    if (tls) {
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.starttls.required", "true");
    }
    final boolean auth = t.getSmtpUsername() != null && !t.getSmtpUsername().isBlank();
    if (auth) {
      props.put("mail.smtp.auth", "true");
      return Session.getInstance(props, new jakarta.mail.Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(t.getSmtpUsername(),
              t.getSmtpPassword() == null ? "" : t.getSmtpPassword());
        }
      });
    }
    return Session.getInstance(props);
  }

  /**
   * The actual {@code Transport.send()} call — separated as a
   * package-private seam so tests can override and stub it out without
   * mockito static-mocking. Tests subclass and override; production
   * goes through this default.
   */
  protected void doSend(Session session, MimeMessage msg) throws MessagingException {
    Transport.send(msg);
  }

  /** Comma-or-semicolon-separated recipients → {@link Address}[] */
  private static Address[] parseAddresses(String raw) throws AddressException {
    String normalised = raw.replace(';', ',');
    return InternetAddress.parse(normalised, false);
  }
}
