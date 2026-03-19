package de.dlr.shepard.common.exceptions;

import jakarta.ws.rs.core.Response.Status;
import java.io.Serial;

public class ShepardParserException extends ShepardException {

  @Serial
  private static final long serialVersionUID = 2L;

  public ShepardParserException() {
    super("A parser error occurred", Status.BAD_REQUEST);
  }

  public ShepardParserException(String message) {
    super(message, Status.BAD_REQUEST);
  }
}
