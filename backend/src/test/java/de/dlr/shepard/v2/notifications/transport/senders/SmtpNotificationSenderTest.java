package de.dlr.shepard.v2.notifications.transport.senders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationMessage;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * NTF1-BACKEND-SMTP — sender tests using a subclass-override seam.
 *
 * <p>The {@code doSend(Session, MimeMessage)} call is overridden in
 * each test subclass to capture what would have been sent — no Mockito
 * static-mocking, no network access.
 */
class SmtpNotificationSenderTest {

  @Test
  void kind_isSMTP() {
    assertEquals(TransportKind.SMTP, new SmtpNotificationSender().kind());
  }

  @Test
  void send_buildsAndDispatchesMimeMessageWithExpectedFields() throws Exception {
    AtomicReference<MimeMessage> captured = new AtomicReference<>();
    SmtpNotificationSender sender = new SmtpNotificationSender() {
      @Override
      protected void doSend(Session session, MimeMessage msg) {
        captured.set(msg);
      }
    };

    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-1");
    t.setKind(TransportKind.SMTP.name());
    t.setSmtpHost("smtp.test");
    t.setSmtpPort(587);
    t.setSmtpFrom("noreply@example.org");
    t.setSmtpTls(true);

    NotificationMessage msg = new NotificationMessage(
        "alice@example.org",
        "Test subject",
        "Test body",
        "https://example.org/detail/42");

    boolean ok = sender.send(t, msg);
    assertTrue(ok);

    MimeMessage captured0 = captured.get();
    assertNotNull(captured0);
    assertEquals("Test subject", captured0.getSubject());
    assertEquals("noreply@example.org", captured0.getFrom()[0].toString());
    assertEquals("alice@example.org", captured0.getRecipients(Message.RecipientType.TO)[0].toString());
    // Body is plain-text with the action URL appended.
    String content = (String) captured0.getContent();
    assertTrue(content.contains("Test body"));
    assertTrue(content.contains("https://example.org/detail/42"));
  }

  @Test
  void send_appliesStartTlsWhenSmtpTlsTrue() {
    SmtpNotificationSender sender = new SmtpNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setSmtpHost("smtp.test");
    t.setSmtpPort(587);
    t.setSmtpTls(true);

    Session session = sender.buildSession(t);
    Properties p = session.getProperties();
    assertEquals("smtp.test", p.getProperty("mail.smtp.host"));
    assertEquals("587", p.getProperty("mail.smtp.port"));
    assertEquals("true", p.getProperty("mail.smtp.starttls.enable"));
    assertEquals("true", p.getProperty("mail.smtp.starttls.required"));
  }

  @Test
  void send_leavesPlainWhenSmtpTlsNullOrFalse() {
    SmtpNotificationSender sender = new SmtpNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setSmtpHost("smtp.plain");
    t.setSmtpPort(25);
    t.setSmtpTls(null);

    Session session = sender.buildSession(t);
    Properties p = session.getProperties();
    assertEquals(null, p.getProperty("mail.smtp.starttls.enable"));
  }

  @Test
  void send_enablesAuthWhenUsernameProvided() {
    SmtpNotificationSender sender = new SmtpNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setSmtpHost("smtp.test");
    t.setSmtpUsername("user");
    t.setSmtpPassword("pass");

    Session session = sender.buildSession(t);
    assertEquals("true", session.getProperties().getProperty("mail.smtp.auth"));
  }

  @Test
  void send_returnsFalseWhenSmtpHostMissing() {
    SmtpNotificationSender sender = new SmtpNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setSmtpFrom("noreply@test");

    boolean ok = sender.send(t, new NotificationMessage("alice@test", "subj", "body", null));
    assertFalse(ok);
  }

  @Test
  void send_returnsFalseWhenSmtpFromMissing() {
    SmtpNotificationSender sender = new SmtpNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setSmtpHost("smtp.test");

    boolean ok = sender.send(t, new NotificationMessage("alice@test", "subj", "body", null));
    assertFalse(ok);
  }

  @Test
  void send_returnsFalseWhenRecipientMissing() {
    SmtpNotificationSender sender = new SmtpNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setSmtpHost("smtp.test");
    t.setSmtpFrom("noreply@test");

    boolean ok = sender.send(t, new NotificationMessage(null, "subj", "body", null));
    assertFalse(ok, "no recipient → false (caller logs)");
  }

  @Test
  void send_returnsFalseOnDoSendMessagingException() {
    SmtpNotificationSender sender = new SmtpNotificationSender() {
      @Override
      protected void doSend(Session session, MimeMessage msg) throws MessagingException {
        throw new MessagingException("simulated SMTP failure");
      }
    };
    NotificationTransport t = new NotificationTransport();
    t.setSmtpHost("smtp.test");
    t.setSmtpFrom("noreply@test");

    boolean ok = sender.send(t, new NotificationMessage("alice@test", "subj", "body", null));
    assertFalse(ok, "MessagingException → false, not propagated");
  }

  @Test
  void send_throwsIllegalArgumentOnNullArgs() {
    SmtpNotificationSender sender = new SmtpNotificationSender();
    assertThrows(IllegalArgumentException.class, () -> sender.send(null, null));
  }
}
