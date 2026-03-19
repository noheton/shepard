package de.dlr.shepard.common.exceptions;

import jakarta.ws.rs.core.Response.Status;
import java.io.Serial;

public class ShepardProcessingException extends ShepardException {

  @Serial
  private static final long serialVersionUID = 1125700124551787161L;

  public ShepardProcessingException(String message) {
    super(message, Status.INTERNAL_SERVER_ERROR);
  }
}
