package de.dlr.shepard.v2.notifications.transport.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * NTF1-BACKEND-LIST — read-side wire shape for
 * {@link NotificationTransport}.
 *
 * <p><b>Load-bearing invariant.</b> This record OMITS
 * {@code smtpPassword} and {@code matrixAccessToken} entirely — they
 * are not fields of the record, not nullable fields set to {@code null},
 * not Jackson-suppressed. The compiler enforces the omission. A
 * regression test ({@code NotificationTransportReadIOTest}) serialises
 * a populated entity through {@link #from(NotificationTransport)} and
 * asserts the resulting JSON contains neither {@code "password"} nor
 * {@code "accessToken"}.
 *
 * <p>The companion {@code NotificationTransportWriteIO} (next commit,
 * NTF1-BACKEND-CRUD) DOES include the secret fields — write paths need
 * them. Keeping the two IOs separate is the architectural seam that
 * prevents a future refactor from accidentally exposing credentials via
 * the read path.
 */
@Schema(description = "Read-only representation of a notification transport configuration; credential fields are omitted by design.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationTransportReadIO(
    String appId,
    String kind,
    String name,
    boolean enabled,
    String lastTestResult,
    Long lastTestedAt,
    String lastTestDetail,
    // SMTP fields — password OMITTED by design (compile-time guarantee).
    String smtpHost,
    Integer smtpPort,
    String smtpUsername,
    String smtpFrom,
    Boolean smtpTls,
    // Matrix fields — accessToken OMITTED by design (compile-time guarantee).
    String matrixHomeserver,
    String matrixDefaultRoom
) {

  /**
   * Project an entity to its read-side IO. Credential fields
   * ({@code smtpPassword}, {@code matrixAccessToken}) are NOT read
   * from the entity — they are absent from the record's constructor
   * signature.
   */
  public static NotificationTransportReadIO from(NotificationTransport t) {
    return new NotificationTransportReadIO(
        t.getAppId(),
        t.getKind(),
        t.getName(),
        t.isEnabled(),
        t.getLastTestResult(),
        t.getLastTestedAt(),
        t.getLastTestDetail(),
        t.getSmtpHost(),
        t.getSmtpPort(),
        t.getSmtpUsername(),
        t.getSmtpFrom(),
        t.getSmtpTls(),
        t.getMatrixHomeserver(),
        t.getMatrixDefaultRoom()
    );
  }
}
