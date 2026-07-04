package de.dlr.shepard.common.exceptions;

import jakarta.ws.rs.core.Response.Status;
import java.io.Serial;

/**
 * Thrown when a write operation detects a conflict with existing data and the
 * caller did not opt into overwrite behaviour.
 *
 * <p>Maps to HTTP 409 Conflict via {@link ShepardExceptionMapper}.
 * Use for duplicate-point detection on the timeseries VALUES INSERT path
 * (see {@code TS-CONFLICT-POLICY-1} in {@code aidocs/16}).
 */
public class ConflictException extends ShepardException {

  @Serial
  private static final long serialVersionUID = 7312045839127403291L;

  private static final Status STATUS = Status.CONFLICT;

  public ConflictException(String message) {
    super(message, STATUS);
  }

  public ConflictException(String format, Object... args) {
    super(format.formatted(args), STATUS);
  }
}
