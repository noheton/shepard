package de.dlr.shepard.common.exceptions;

import jakarta.ws.rs.core.Response.Status;
import java.io.Serial;

/**
 * Thrown when a write conflicts with existing data — e.g. a duplicate
 * {@code (timeseries_id, time)} pair on a unique constraint. Maps to
 * HTTP 409 Conflict.
 */
public class ConflictException extends ShepardException {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Status STATUS = Status.CONFLICT;

  public ConflictException(String message) {
    super(message, STATUS);
  }

  public ConflictException(String format, Object... args) {
    super(format.formatted(args), STATUS);
  }
}
