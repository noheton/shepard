package de.dlr.shepard.common.exceptions;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import java.io.Serial;

public abstract class ShepardException extends WebApplicationException {

  @Serial
  private static final long serialVersionUID = 4144046935461575595L;

  protected ShepardException(String message, Status status) {
    super(message, status);
  }
}
